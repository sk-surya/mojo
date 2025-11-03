package org.skor.jhighs.wrapper;

/**
 * Variable types in HiGHS.
 */
public enum HighsVarType {
    kContinuous(0),
    kInteger(1),
    kSemiContinuous(2),
    kSemiInteger(3),
    kImplicitInteger(4);
    
    private final int value;
    
    HighsVarType(int value) {
        this.value = value;
    }
    
    public int getValue() {
        return value;
    }
    
    public static HighsVarType fromValue(int value) {
        for (HighsVarType type : values()) {
            if (type.value == value) {
                return type;
            }
        }
        return kContinuous;
    }
}
