/*
 * Copyright (c) 2011, Todd Cook.
 *  All rights reserved.
 *
 *  Redistribution and use in source and binary forms, with or without modification,
 *  are permitted provided that the following conditions are met:
 *
 *      * Redistributions of source code must retain the above copyright notice,
 *        this list of conditions and the following disclaimer.
 *      * Redistributions in binary form must reproduce the above copyright notice,
 *        this list of conditions and the following disclaimer in the documentation
 *        and/or other materials provided with the distribution.
 *      * Neither the name of the <ORGANIZATION> nor the names of its contributors
 *        may be used to endorse or promote products derived from this software
 *        without specific prior written permission.
 *
 *  THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND
 *  ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED
 *  WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
 *  DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT HOLDER OR CONTRIBUTORS BE LIABLE
 *  FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL
 *  DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR
 *  SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER
 *  CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY,
 *  OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE
 *  OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */

package com.github.toddcook.projecteuler

/**
 * Problem 29
 *
 * Consider all integer combinations of a^(b) for 2 <= a <= 5 and 2 <= b <= 5:
 *
 *  2^(2)=4, 2^(3)=8, 2^(4)=16, 2^(5)=32
 *  3^(2)=9, 3^(3)=27, 3^(4)=81, 3^(5)=243
 *  4^(2)=16, 4^(3)=64, 4^(4)=256, 4^(5)=1024
 *  5^(2)=25, 5^(3)=125, 5^(4)=625, 5^(5)=3125
 *
 *  If they are then placed in numerical order, with any repeats removed,
 *  we get the following sequence of 15 distinct terms:
 *
 *  4, 8, 9, 16, 25, 27, 32, 64, 81, 125, 243, 256, 625, 1024, 3125
 *
 *  How many distinct terms are in the sequence generated by a^(b) for
 *  2 <= a <= 100 and 2 <= b <= 100?
 *
 * @author : Todd Cook
 * @since : 4/24/2011
 */

import scala.collection.mutable.ListBuffer

object problem_29 {

  def computeTerms (termOneFloor: Int, termOneCeiling: Int,
                    termTwoFloor: Int, termTwoCeiling: Int) = {
    var term1Range = (termOneFloor to termOneCeiling).toList
    var term2Range = (termTwoFloor to termTwoCeiling).toList
    var terms = new ListBuffer[Double]()
    term1Range.foreach(a => term2Range.foreach(b =>
                                                 terms.append(java.lang.Math.pow(a, b))))
    var uniqueTerms = terms.toList.sortWith(_ < _).distinct
    uniqueTerms
  }

  def answer = computeTerms(2, 100, 2, 100).length

  def main (args: Array[String]) = {
    //   println( computeTerms(2, 5, 2, 5)  ) // for the unit test
    println(answer)
  }
}

/**
 * Commentary:
 * can't use Long data type:
 * scala> java.lang.Math.pow(100, 100)
res138: Double = 1.0E200

scala> res138.toLong
res139: Long = 9223372036854775807

 *
 */