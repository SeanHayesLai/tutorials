package com.github.hoffart.dmap;

import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.ByteBuffer;
import java.nio.MappedByteBuffer;
import java.nio.channels.FileChannel.MapMode;
import java.util.*;

import com.github.hoffart.dmap.util.CompressionUtils;
import com.github.hoffart.dmap.util.ExtendedFileChannel;
import gnu.trove.impl.Constants;
import gnu.trove.map.hash.TObjectIntHashMap;
import org.iq80.snappy.Snappy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.github.hoffart.dmap.util.ByteArray;
import com.github.hoffart.dmap.util.ByteArrayUtils;
import com.github.hoffart.dmap.util.map.CachingHashMap;

/**
 * Disk-backed implementation of a very simple Map that supports only
 */
public class DMap {
  public static final int VERSION = 4;

  private static final int DEFAULT_BLOCK_CACHE_COUNT = 250;

  /** Current Map file generated by Builder has Global trailer offset at 13. */
  protected static final int DEFAULT_LOC_FOR_TRAILER_OFFSET = 13;

  /** Map file with data. */
  private final File mapFile_;
  private final ExtendedFileChannel raf_;

  /** Number of entries in the map. */
  private final int size;
  
  /** The block size */
  private final int blockSize;
  
  /** Indicates if the values are compressed */
  private final boolean valuesCompressed;

  /** Maximum number of blocks that can are held in memory when value blocks are held on disk. */
  private final int cacheBlockCount_;

  /** First Key - Mapped block pair. */
  private final Map<ByteArray, MappedByteBuffer>  cachedByteBuffers_;

  /** Mapping of first key of block to block's start offset. */
  private final Map<ByteArray, Long> firstKeyInBlock_;

  /** Mapping of block start offset to block trailer offset. */
  private final Map<Long, Long> blockOffsetInfo_;

  /** Flag to enable/disable preloading of key offset pairs. */
  private final boolean preloadAllKeyOffsets;

  /** Flag to enable/disable preloading of all the values. */
  private final boolean preloadAllValues;
  
  /** Trove Map load factor (default: 0.5) */
  private final float troveLoadFactor = Constants.DEFAULT_LOAD_FACTOR;
  
  /** Trove Map no Entry value (default: -1) */
  private final int troveNoEntryValue = -1;

  /** Mapping of BlockTrailer Start offset and block trailer mapped bytebuffer of trailer. */
  private final Map<Long, MappedByteBuffer> blockTrailerBuffer_;

  /** Mapping of BlockTrailer start offset and all key-offset pairs info contained in the trailer. */
  private final Map<Long, TObjectIntHashMap<ByteArray>> blockTrailerKeys;

  /** First keys of all the blocks present in the dmap loaded once. */
  private ByteArray[] firstKeys;

  private final Logger logger_ = LoggerFactory.getLogger(DMap.class);

  private DMap(Builder loader) throws IOException {
    mapFile_ = loader.mapFile_;
    preloadAllKeyOffsets = loader.preloadOffsets_;
    preloadAllValues = loader.preloadValues_;
    cacheBlockCount_ = loader.cacheBlockSize_;

    raf_ = new ExtendedFileChannel(new RandomAccessFile(mapFile_, "r").getChannel());

    int version = raf_.readInt();
    if(version != VERSION) {
      throw new IOException("Invalid version of DMap file encountered. Please fix.");
    }

    // Sorted first keys: firstKey->blockStart
    firstKeyInBlock_ = new TreeMap<>();
    // blockStartOffset->blockTrailerOffset
    blockOffsetInfo_ = new HashMap<>();
    // without pre load key-offset, put BlockTrailer total to mem:<blockTrailerStartOffset, BlockTrailer>
    blockTrailerBuffer_ = new HashMap<>();
    // BlockTrailerStartOffset map to <key, offset of value in current block>
    blockTrailerKeys = new HashMap<>();

    size = raf_.readInt();
    blockSize = raf_.readInt();
    valuesCompressed = raf_.readBool();

    if (size == 0) {
      cachedByteBuffers_ = new CachingHashMap<>(0);
      return;
    }

    //加载BlockTrailer里的key->offset到内存中,如果设置了preloadAllKeyOffsets的话
    //否则直接把BlockTrailer加载到内存中.这样在get(key)时,需要解析BlockTrailer信息
    loadKeyDetails();

    //把value也加载到内存里.cachedByteBuffers_是每个Block的firstKey和整个数据块的内容的映射
    if (preloadAllValues) {
      int numBlocks = getBlockCount();
      // override the cacheBlockCount_
      cachedByteBuffers_ = new CachingHashMap<>(numBlocks);
      for(ByteArray firstKey : firstKeyInBlock_.keySet()) {
        // firstKey --------------> blockStart ----------------> blockTrailerStart
        //          firstKeyInBlock            blockOffsetInfo

        //Block-A|Block-A-Trailer|Block-B|Block-B-Trailer
        //|blockStart
        //       |blockTrailerStart
        //|<---->|
        // mappedBuffer
        long blockStart = firstKeyInBlock_.get(firstKey);
        long blockTrailerStart = blockOffsetInfo_.get(blockStart);
        //定位到blockStart位置,读取长度为blockTrailerStart-blockStart,即整个数据块的内容加载到内存中
        MappedByteBuffer mappedBuffer_ = raf_.map(MapMode.READ_ONLY, blockStart, blockTrailerStart - blockStart);
        mappedBuffer_.load();
        //缓存里存放的是每个块的第一个firstKey和整个数据块的内容
        cachedByteBuffers_.put(firstKey, mappedBuffer_);
      }
      logger_.debug("Preloaded all " + numBlocks + " blocks.");
    } else
      //如果没有事先加载,则先构建一个Map. 在get的时候再放入
      cachedByteBuffers_ = new CachingHashMap<>(cacheBlockCount_);
  }

  /*
   * This public Builder class allows creation of customized DMap instance.
   * This is the Only way to create a DMap instance.
   */
  public static class Builder {
    private boolean preloadOffsets_;
    private boolean preloadValues_;
    private int cacheBlockSize_;
    private final File mapFile_;

    /**
     * A Loader constructor that takes a File parameter to be loaded into DMap.
     *
     * @param mapFile A File instance to be loaded.
     */
    public Builder(File mapFile) {
      mapFile_ = mapFile;
      cacheBlockSize_ = DEFAULT_BLOCK_CACHE_COUNT;
      // by default, both keyoffset loading and value loading will be disabled
      preloadOffsets_ = false;
      preloadValues_ = false;
    }

    /**
     * This method enables key-offset preloading during DMap instantiation
     *
     * @return The current Loader instance.
     */
    public Builder preloadOffsets() {
      this.preloadOffsets_ = true;
      return this;
    }

    /**
     * This method enables values preloading during DMap instantiation
     *
     * @return The current Loader instance.
     */
    public Builder preloadValues() {
      this.preloadValues_ = true;
      return this;
    }

    /**
     * This method sets the DMap block limit to specified value
     *
     * @return The current Loader instance.
     */
    public Builder setMaxBlockLimit(int value) {
      this.cacheBlockSize_ = value;
      return this;
    }

    /**
     * The parameter-less build method creates an instance of DMap.
     * This method needs to be called once all DMap customizations are done.
     *
     * @return A DMap instance.
     * @throws IOException
     */
    public DMap build() throws IOException {
      return new DMap(this);
    }
  }

  /**
   * Get the number of entries in the map.
   * 
   * @return Number of entries in the map.
   */
  public int size() {
    return size;
  }

  
  /**
   * Get the size of a block in bytes
   * 
   * @return Size of the blocks
   */
  public int getBlockSize() {
    return blockSize;
  }
  
  /**
   * Get number of blocks in the map.数据块的数量
   * 
   * @return Number of blocks in the map.
   * @throws IOException
   */
  public synchronized int getBlockCount() throws IOException {
    //首先定位到GlobalTrailerOffset,在DMapBuilder.build的最末尾,这个位置开始首先写入BlockCount
    long trailerOffset = getGlobalTrailerOffset();
    raf_.position(trailerOffset);
    //读取出BlockCounts
    return raf_.readVInt();
  }

  /**
   * Get byte[] value for key.
   * 
   * @param key Key to retrieve the value for.
   * @return  byte[] associated with key.
   */
  public byte[] get(byte[] key) throws IOException {
    if (size == 0) return null;
    
    ByteArray keyBytes = new ByteArray(key);
    logger_.debug("get(" + keyBytes + ") - hash: " + keyBytes.hashCode());
    // identify the block containing the given key using first key information.
    // firstKeys is settup at loadKeyDetails()
    ByteArray firstKeyBytes = ByteArrayUtils.findMaxElementLessThanTarget(firstKeys, keyBytes);

    // key not in range (less than start key)
    if(firstKeyBytes == null) return null;

    //firstKey-->所在的Block的startOffset-->BlockTrailerOffset
    //要获取key对应的value, 首先要找到key对应的value,其中value在Block中的Offset, 这个信息记录在BlockTrailer里
    long blockStart = firstKeyInBlock_.get(firstKeyBytes);
    long blockTrailerStart = blockOffsetInfo_.get(blockStart);
    // load the value offset
    int valueOffset = getValueOffset(keyBytes, blockTrailerStart);
    if (valueOffset == troveNoEntryValue) return null;

    //缓存: 一整个Block的数据都缓存起来. 如果是顺序读的话,因为知道了Block的firstKey,
    //只要读取了Block的第一个key, 这个Block其余的key也都加载进内存中.而不必从文件中读取了
    MappedByteBuffer blockMapBuffer = cachedByteBuffers_.get(firstKeyBytes);
    if(blockMapBuffer == null) {
      synchronized (cachedByteBuffers_) {
        blockMapBuffer = cachedByteBuffers_.get(firstKeyBytes);
        if (blockMapBuffer == null) {
          //起始位置是BlockStart, 读取的数量=BlockTrailerOffset-BlockStartOffset=这个所有value的长度
          //|val1,val2,...|
          //|<BlockStart  |<BlockTrailerOffset
          //后者减去前者就是当前Block所有的value了
          blockMapBuffer = raf_.map(MapMode.READ_ONLY, blockStart, blockTrailerStart - blockStart);
          //缓存的key是Block的firstKey, 缓存的内容是这个Block的所有数据内容
          //整个过程要做的工作和preloadAllValues=true时在构造函数里的工作一样.都是要加载整个数据块的内容到内存中
          cachedByteBuffers_.put(firstKeyBytes, blockMapBuffer);
        }
      }
    }

    //上一步已经将当前Block放进内存中了,现在要获取value的值,因为知道了value在Block中的offset,可以直接从内存中get出来
    ByteBuffer slice = blockMapBuffer.slice();
    //定位到value所在的offset位置
    slice.position(valueOffset);
    //数据value的格式是valLen,然后是value,所以依次读取
    int valueLength = CompressionUtils.readVInt(slice);
    byte[] value = new byte[valueLength];
    slice.get(value);
    //如果写入的时候经过压缩,读取的时候就要解压缩
    if (valuesCompressed)
      value = Snappy.uncompress(value, 0, value.length);
    return value;
  }

  /* NOTE:
   * When Offset preloading is disabled, this method does a linear search over all the keys in the given block
   * to find the matching key and retrieve the value offset associated with the key.
   * Searching single block DMap contaning N keys will be slower than Searching M-Blocks DMap
   * with each block containing a subset of key.
   */
  private int getValueOffset(ByteArray keyBytes, Long blockTrailerStartOffset) throws IOException {
    int valueOffset = troveNoEntryValue;
    //没有预加载key-offset的话,要从blockTrailerBuffer_中自己去解析出来
    if(!preloadAllKeyOffsets) {
      // time for linear search over the keys in block using mappedTrailer
      // 现在trailerBuffer里放的是整个BlockTrailer的信息. 对应的内容就是DMapBuilder.updateBlockTrailer()
      ByteBuffer trailerBuffer = blockTrailerBuffer_.get(blockTrailerStartOffset).slice();
      // load key count - int
      int numKeysInBlock = CompressionUtils.readVInt(trailerBuffer);

      // start search over keys 循环当前BlockTrailer里的所有key,判断和要查询的keyBytes是否一样,查到则找到offset
      for(int count=0; count<numKeysInBlock; count++) {
        int keyLen = CompressionUtils.readVInt(trailerBuffer);
        byte[] currentkey = new byte[keyLen];
        trailerBuffer.get(currentkey);
        ByteArray currentKeyBytes = new ByteArray(currentkey);
        int offset = CompressionUtils.readVInt(trailerBuffer);
        logger_.debug("Comparing " + keyBytes + " and " + currentKeyBytes + " : " + keyBytes.compareTo(currentKeyBytes));
        if(keyBytes.compareTo(currentKeyBytes) == 0) {
          valueOffset = offset;
          break;
        }
      }
    } else {
      // just look up in the existing map
      //value的offset记录在BlockTrailer里.
      //value看做Block,value之后的内容是BlockTrailer. BlockTrailer里记录了key的数量,key,以及key对应的value在Block中的offset
      //BlockTrailer的中文意思是Block的跟踪者. 跟踪者要能记录Block中的信息,才叫做跟踪.
      //对于Map而言,key对应value是很自然的. 通过key对应value在Block中的offset,可以间接地对应到value
      //value1,value2,...key1,value1'offset
      //  |<------------------------|
      TObjectIntHashMap tmpMap = blockTrailerKeys.get(blockTrailerStartOffset);
      if(tmpMap != null) {
        valueOffset = tmpMap.get(keyBytes);
      }
    }
    return valueOffset;
  }

  // 定位到Header的postion=13位置,读取出里面的值,这个值是GlobalTrailerOffset在文件中的位置
  private long getGlobalTrailerOffset() throws IOException {
    raf_.position(DEFAULT_LOC_FOR_TRAILER_OFFSET);
    return raf_.readLong();
  }

  /**
   *
   * @param trailerStartOffset BlockTrailer的start-offset
   * @param trailerSize 这个值的计算方式是下一个Block的start减去前一个Block的Trailer.
   * @throws IOException
   */
  private void processBlockTrailer(long trailerStartOffset, long trailerSize) throws IOException {
    //将BlockTrailer的内存都加载到内存中. BlockTrailer的信息是在DMapBuilder.updateBlockTrailer写入的
    //主要是要将key和value在Block中的offset映射起来. 这样根据key能找到offset,从而在Block的offset处开始读取结果数据
    MappedByteBuffer trailerBuffer = raf_.map(MapMode.READ_ONLY, trailerStartOffset, trailerSize);
    if(!preloadAllKeyOffsets) {
      //不预先加载keyOffset.这里的keyOffset中的offset指的是key对应的value在Block中的offset.
      //如果预先加载到内存中,则key对应的value的offset都在内存中.要查找key对应的value时,
      //直接获取tmpKeyOffsetMap.get(key)得到offset,然后定位到Block的offset开始读取数据.
      //如果没有预先加载,相当于BlockTrailer这部分信息要在get(key)的时候每次都解析一次.
      //放到内存的好处是事先把BlockTrailer的信息都解析出来.这样在get时,直接从内存获取,无需解析.
      blockTrailerBuffer_.put(trailerStartOffset, trailerBuffer);
    } else {
      //BlockTrailer首先写入当前Block的key数量,因为写入的使用使用writeVInt,对应读取的时候就用readVInt
      int numKeysInBlock = CompressionUtils.readVInt(trailerBuffer);
      //构造和key数量相符(初始容量)的Map
      TObjectIntHashMap<ByteArray> tmpKeyOffsetMap = 
        new TObjectIntHashMap<>((int) (numKeysInBlock/troveLoadFactor+0.5), troveLoadFactor, troveNoEntryValue);

      //读取的顺序和在DMapBuilder.updateBlockTrailer中写入的顺序一样
      for(int count=0; count<numKeysInBlock; count++) {
        //keyLen, key, key对应的value在当前Block的offset
        int keyLen = CompressionUtils.readVInt(trailerBuffer);
        byte[] currentkey = new byte[keyLen];
        trailerBuffer.get(currentkey);
        ByteArray currentKeyBytes = new ByteArray(currentkey);
        int offset = CompressionUtils.readVInt(trailerBuffer);
        //key->value在当前Block的offset
        tmpKeyOffsetMap.put(currentKeyBytes, offset);
      }
      //BlockTrailer的开始位置--> <key-->value在Block里的offset>
      //因为一个DataBlock写入了多个value,同样BlockTrailer里也记录了多个key.
      //blockTrailerKeys记录的是一个BlockTrailer的offset, 以及在这里面的所有key和所有value在Block中的offset
      blockTrailerKeys.put(trailerStartOffset, tmpKeyOffsetMap);
    }
  }

  private void loadKeyDetails() throws IOException {
    // 定位到GlobalTrailerOffset,并读取出BlockCounts.
    int numBlocks = getBlockCount();
    logger_.debug("Number of blocks in file : " + numBlocks);
    long blockStart;
    long blockTrailerStart;
    long prevBlockTrailerStart = -1;

    for(int blockCount = 0; blockCount < numBlocks; ++blockCount) {
      //写入的顺序对应DMapBuilder的step8:BlockStart, BlockTrailer, firstKeyLen, firstKey
      blockStart = raf_.readVLong();
      blockTrailerStart = raf_.readVLong();
      int firstKeySize = raf_.readVInt();
      byte[] firstKeyBytes = new byte[firstKeySize];
      raf_.read(firstKeyBytes);

      firstKeyInBlock_.put(new ByteArray(firstKeyBytes), blockStart);
      blockOffsetInfo_.put(blockStart, blockTrailerStart);
      //第一个数据块没有前面的数据块.所以第一个数据块不会调用processBlockTrailer
      //Block-1|Block-1-Trailer|Block-2|Block-2-Trailer
      //|      |prevBlockTrailerStart
      //blockStart
      //第二个数据块,blockCount=1. 文件的格式为(数据块之间以|分隔):
      //Block-1|Block-1-Trailer|Block-2|Block-2-Trailer
      //       |prevBlockTrailerStart
      //                       |blockStart
      //       |<------------->|
      //         BlockTrailer to be processed. firstParam:prevBlockTrailerStart, secParam:trailerSize
      // 因为第一次循环没有处理,第二次循环处理第一个BlockTrailer. 当到达最后一次循环时,处理的是倒数第二个BlockTrailer
      // 所以在循环外面还需要最后一次处理,处理最后一个BlockTrailer.
      if(blockCount > 0) {
        // compute the previous block's trailer size and store the information
        processBlockTrailer(prevBlockTrailerStart, blockStart - prevBlockTrailerStart);
      }
      prevBlockTrailerStart = blockTrailerStart;
    }
    processBlockTrailer(prevBlockTrailerStart, getGlobalTrailerOffset()-prevBlockTrailerStart);

    // load all the first keys for binary search during get()
    firstKeys = new ByteArray[firstKeyInBlock_.size()];
    firstKeyInBlock_.keySet().toArray(firstKeys);
  }

  /**
   * This method returns an iterator for this DMap with different implementations for different preload settings.
   * These iterators are NOT thread save.
   * @return an iterator for the current dmap
   */
  public EntryIterator entryIterator() {
    if (preloadAllKeyOffsets)
      return new EntryIteratorForPreloadedKeys();
    else 
      return new EntryIteratorWithoutPreloading();
  }

  private class EntryIteratorWithoutPreloading implements EntryIterator {
    Iterator<MappedByteBuffer> blockIterator_;
    ByteBuffer curBuffer_;
    int curBlockKeyNum_;
    int curKey_;

    //如果没有预加载key-offset,则可用的是BlockTrailer的信息:blockTrailerBuffer_:<BlockTrailerOffset,BlockTrailer>
    private EntryIteratorWithoutPreloading() {
      //the iterator of BlockTrailer. 用于迭代每个BlockTrailer
      //next方法的迭代由用户控制,循环读取BlockTrailer的每一对信息
      //实际上是否控制执行下一个BlockTrailer都是在next()方法中执行.
      blockIterator_ = blockTrailerBuffer_.values().iterator();
      if (blockIterator_.hasNext()) {
        // current BlockTrailer
        curBuffer_ = blockIterator_.next().slice();
        // the first data is the number of keys in this block/blockTrailer
        curBlockKeyNum_ = CompressionUtils.readVInt(curBuffer_);
      } else {
        curBuffer_ = null;
        curBlockKeyNum_ = 0;
      }
      curKey_ = 0;
    }

    @Override
    public boolean hasNext() {
      return curKey_ < curBlockKeyNum_ || blockIterator_.hasNext();
    }

    @Override
    public Entry next() throws IOException {
      // TODO: make it more efficient if necessary
      if (curKey_++ < curBlockKeyNum_) {
        // keyLen, key, offset
        int keyLen = CompressionUtils.readVInt(curBuffer_);
        byte[] key = new byte[keyLen];
        curBuffer_.get(key);
        // skip offset, 因为next循环要能够定位到每一个|keyLen,key,offset|的边界
        CompressionUtils.readVInt(curBuffer_);
        // Entry的两个参数分别是key和对应的value.
        return new Entry(key, get(key));
      } else if (blockIterator_.hasNext()) {
        // 一个BlockTrailer已经读取完了.判断是否有下一个BlockTrailer,有的话,重置相关变量,下一次从if里执行
        curBuffer_ = blockIterator_.next().slice();
        // 第一个BlockTrailer的这三个变量都在构造函数里,接下来的BlockTrailer在这里设置
        curBlockKeyNum_ = CompressionUtils.readVInt(curBuffer_);
        curKey_ = 0;
        // 递归调用.
        return next();
      } else 
        return null;
    }
  }

  private class EntryIteratorForPreloadedKeys implements EntryIterator {
    Iterator<TObjectIntHashMap<ByteArray>> blockIterator_;
    Iterator<ByteArray> keyIterator_;
    Entry nextEntry;

    // 如果预加载了key-offset,则从blockTrailerKeys中获取:<BlockTrailerOffset,<key,offset>>
    private EntryIteratorForPreloadedKeys() {
      blockIterator_ = blockTrailerKeys.values().iterator();
      if (blockIterator_.hasNext()) {
        keyIterator_ = blockIterator_.next().keySet().iterator();
      } else
        keyIterator_ = null;
      nextEntry = null;
    }

    @Override
    public boolean hasNext() throws IOException {
      if (nextEntry == null)
        nextEntry = getNextEntry();
      return nextEntry != null;
    }

    @Override
    public Entry next() throws IOException {
      if (nextEntry == null)
        nextEntry = getNextEntry();
      Entry tmpNextEntry = nextEntry;
      nextEntry = null;
      return tmpNextEntry;
    }
    
    private Entry getNextEntry() throws IOException {
      // TODO: make it more efficient if necessary
      // 当前BlockTrailer里的key->offset的迭代
      if (keyIterator_ != null && keyIterator_.hasNext()) {
        ByteArray key = keyIterator_.next();
        return new Entry(key.getBytes(), get(key.getBytes()));
      }
      //当前BlockTrailer里的List都读取完了,读取下一个BlockTrailer
      //因为blockTrailerKeys放的是BlockTrailerOffset和key->offset的映射.
      //if里执行的是key->offset的迭代
      while (blockIterator_.hasNext()) {
        keyIterator_ = blockIterator_.next().keySet().iterator();
        if (keyIterator_.hasNext())
          //还是递归调用. 一个BlockTrailer读取完毕,要接着下一个BlockTrailer里的key->offset
          return getNextEntry();
      }
      return null;
    }
  }

  /**
   * A not thread save iterator for DMap entries (byte[], byte[])
   */
  public static interface EntryIterator {
    public boolean hasNext() throws IOException;
    public Entry next() throws IOException;
  }
  
  public static class Entry {
    private byte[] key;
    private byte[] value;

    private Entry(byte[] key, byte[] value) {
      this.key = key;
      this.value = value;
    }

    public byte[] getKey() {
      return key;
    }

    public byte[] getValue() {
      return value;
    }
  }
}