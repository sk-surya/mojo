package org.skor.jhighs.wrapper;

/**
 * Objective sense (minimize or maximize).
 */
public enum ObjSense {
    kMinimize(1),
    kMaximize(-1);
    
    private final int value;
    
    ObjSense(int value) {
        this.value = value;
    }
    
    public int getValue() {
        return value;
    }
    
    public static ObjSense fromValue(int value) {
        for (ObjSense sense : values()) {
            if (sense.value == value) {
                return sense;
            }
        }
        return kMinimize;
    }
}
