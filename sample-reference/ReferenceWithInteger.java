package com.example.reference;

/**
 * Sample reference file using Integer.valueOf() format
 */
public class ReferenceWithInteger {
    
    /**
     * Success status code
     */
    public static final Integer SUCCESS = Integer.valueOf(0);
    
    /**
     * General error code
     */
    public static final Integer ERROR_GENERAL = Integer.valueOf(1000);
    
    // Authentication error
    public static final Integer ERROR_AUTH_FAILED = Integer.valueOf(1001);
    
    public static final Integer ERROR_INVALID_INPUT = Integer.valueOf(1002); // Invalid input provided
    
    /**
     * Database connection error
     */
    public static final Integer ERROR_DB_CONNECTION = Integer.valueOf(2001);
    
    /**
     * File not found error
     */
    public static final Integer ERROR_FILE_NOT_FOUND = Integer.valueOf(3001);
}