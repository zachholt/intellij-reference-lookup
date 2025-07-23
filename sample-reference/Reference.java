package com.example.reference;

/**
 * Reference constants for error codes, status codes, and other system constants.
 * This file demonstrates how the Reference Lookup plugin can parse Java constants.
 */
public class Reference {
    
    // HTTP Status Codes
    
    /**
     * The request has succeeded.
     */
    public static final int HTTP_OK = 200;
    
    /**
     * The request has been fulfilled and resulted in a new resource being created.
     */
    public static final int HTTP_CREATED = 201;
    
    /**
     * The server cannot or will not process the request due to an apparent client error.
     */
    public static final int HTTP_BAD_REQUEST = 400;
    
    /**
     * Authentication is required and has failed or has not yet been provided.
     */
    public static final int HTTP_UNAUTHORIZED = 401;
    
    /**
     * The request was valid, but the server is refusing action.
     */
    public static final int HTTP_FORBIDDEN = 403;
    
    /**
     * The requested resource could not be found but may be available in the future.
     */
    public static final int HTTP_NOT_FOUND = 404;
    
    /**
     * A generic error message when an unexpected condition was encountered.
     */
    public static final int HTTP_INTERNAL_SERVER_ERROR = 500;
    
    // Database Error Codes
    
    /**
     * SQL syntax error - the SQL statement contains invalid syntax.
     */
    public static final String SQL_ERROR_SYNTAX = "SQL1001";
    
    /**
     * Database connection timeout - unable to establish connection within timeout period.
     */
    public static final String SQL_ERROR_CONNECTION_TIMEOUT = "SQL1002";
    
    /**
     * Deadlock detected - transaction was rolled back due to deadlock.
     */
    public static final String SQL_ERROR_DEADLOCK = "SQL1205";
    
    // Authentication Error Codes
    
    public static final String AUTH_ERROR_INVALID_CREDENTIALS = "AUTH001"; // Invalid username or password
    public static final String AUTH_ERROR_ACCOUNT_LOCKED = "AUTH002"; // Account has been locked due to multiple failed attempts
    public static final String AUTH_ERROR_SESSION_EXPIRED = "AUTH003"; // User session has expired
    public static final String AUTH_ERROR_INSUFFICIENT_PRIVILEGES = "AUTH004"; // User lacks required permissions
    
    // Business Logic Error Codes
    
    /**
     * Validation error - input data does not meet validation requirements.
     * This includes format errors, missing required fields, or invalid values.
     */
    public static final String ERROR_VALIDATION_FAILED = "BUS001";
    
    /**
     * Business rule violation - the operation violates a business rule.
     * For example, trying to withdraw more money than available balance.
     */
    public static final String ERROR_BUSINESS_RULE_VIOLATION = "BUS002";
    
    /**
     * Resource not found - the requested resource does not exist.
     */
    public static final String ERROR_RESOURCE_NOT_FOUND = "BUS003";
    
    /**
     * Duplicate resource - attempting to create a resource that already exists.
     */
    public static final String ERROR_DUPLICATE_RESOURCE = "BUS004";
    
    // System Configuration Constants
    
    public static final int MAX_RETRY_ATTEMPTS = 3;
    public static final int CONNECTION_TIMEOUT_MS = 30000; // 30 seconds
    public static final String DEFAULT_ENCODING = "UTF-8";
    public static final String API_VERSION = "v1.0";
    
    // Feature Flags
    
    /**
     * Enable new authentication flow with multi-factor authentication support.
     */
    public static final boolean FEATURE_NEW_AUTH_FLOW = true;
    
    /**
     * Enable advanced logging with detailed request/response tracking.
     */
    public static final boolean FEATURE_ADVANCED_LOGGING = false;
    
    // API Response Codes
    
    public static final String API_SUCCESS = "SUCCESS"; // Operation completed successfully
    public static final String API_PARTIAL_SUCCESS = "PARTIAL_SUCCESS"; // Some operations succeeded, some failed
    public static final String API_FAILURE = "FAILURE"; // Operation failed
    public static final String API_RATE_LIMIT_EXCEEDED = "RATE_LIMIT"; // Too many requests
    
    // File Operation Codes
    
    /**
     * File not found at the specified path.
     */
    public static final String FILE_ERROR_NOT_FOUND = "FILE001";
    
    /**
     * Permission denied - insufficient permissions to access the file.
     */
    public static final String FILE_ERROR_PERMISSION_DENIED = "FILE002";
    
    /**
     * File is locked by another process.
     */
    public static final String FILE_ERROR_LOCKED = "FILE003";
    
    /**
     * Disk space insufficient to complete the operation.
     */
    public static final String FILE_ERROR_DISK_FULL = "FILE004";
    
    // Network Error Codes
    
    public static final String NET_ERROR_CONNECTION_REFUSED = "NET001"; // Connection refused by remote host
    public static final String NET_ERROR_TIMEOUT = "NET002"; // Network operation timed out
    public static final String NET_ERROR_DNS_FAILURE = "NET003"; // DNS resolution failed
    public static final String NET_ERROR_SSL_HANDSHAKE = "NET004"; // SSL/TLS handshake failed
}