package com.example.reference;

public class Reference {
    
    // HTTP Status Codes
    /** OK - The request has succeeded */
    public static final int HTTP_OK = 200;
    
    /** Created - The request has been fulfilled and resulted in a new resource being created */
    public static final int HTTP_CREATED = 201;
    
    /** Bad Request - The server could not understand the request due to invalid syntax */
    public static final int HTTP_BAD_REQUEST = 400;
    
    /** Unauthorized - The client must authenticate itself to get the requested response */
    public static final int HTTP_UNAUTHORIZED = 401;
    
    /** Forbidden - The client does not have access rights to the content */
    public static final int HTTP_FORBIDDEN = 403;
    
    /** Not Found - The server can not find the requested resource */
    public static final int HTTP_NOT_FOUND = 404;
    
    /** Internal Server Error - The server has encountered a situation it doesn't know how to handle */
    public static final int HTTP_INTERNAL_SERVER_ERROR = 500;

    // Database Error Codes
    public static final String DB_CONNECTION_FAILED = "DB001";
    public static final String DB_QUERY_TIMEOUT = "DB002";
    public static final String DB_CONSTRAINT_VIOLATION = "DB003";
    
    // Application Constants
    public static final int MAX_RETRY_ATTEMPTS = 3;
    public static final long DEFAULT_TIMEOUT_MS = 5000L;
    public static final double TAX_RATE = 0.0825;
    
    // Flags
    public static final boolean FEATURE_FLAG_NEW_UI = true;
    public static final boolean DEBUG_MODE = false;
}
