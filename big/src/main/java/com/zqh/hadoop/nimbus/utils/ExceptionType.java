package com.zqh.hadoop.nimbus.utils;

public enum ExceptionType {
	BROKEN_STREAM,
	FAILED_TO_ACCEPT_CONNECTION,
	FAILED_TO_CONNECT,
	FAILED_TO_START_SERVER,
	PROTOCOL_ERROR,
	UNKNOWN_SERVER_TYPE,
	NO_AVAILABLE_SERVERS,
	FAILED_TO_RECOVER,
	FAILED_TO_CLOSE_WRITE_FILE,
	FAILED_TO_FLUSH_WRITE_FILE,
	MASTER_UNAVAILABLE, NEBULA_EXISTS, NEBULA_DOES_NOT_EXIST, NO_AVAILABLE_PORT, ZOOKEEPER_EXCEPTION, INVALID_CONF, NEBULA_ALREADY_EXISTS, FAILED_TO_CONNECT_TO_ZOOKEEPER
}
