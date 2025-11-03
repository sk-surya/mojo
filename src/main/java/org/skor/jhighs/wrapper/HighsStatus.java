package org.skor.jhighs.wrapper;

/**
 * Status codes returned by HiGHS methods.
 */
public enum HighsStatus {
    OK(0),
    WARNING(1),
    ERROR(-1);
    
    private final int code;
    
    HighsStatus(int code) {
        this.code = code;
    }
    
    public int getCode() {
        return code;
    }
    
    public static HighsStatus fromCode(int code) {
        for (HighsStatus status : values()) {
            if (status.code == code) {
                return status;
            }
        }
        return ERROR;
    }
    
    public boolean isOk() {
        return this == OK;
    }
    
    public boolean isError() {
        return this == ERROR;
    }
}
