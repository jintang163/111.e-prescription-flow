package cn.hospital.eph.common.enums;

public enum UserType {
    DOCTOR(1),
    PHARMACIST(2),
    PATIENT(3),
    ADMIN(9);

    private final int code;

    UserType(int code) {
        this.code = code;
    }

    public int code() {
        return code;
    }

    public String role() {
        return name();
    }

    public static UserType ofCode(int code) {
        for (UserType v : values()) {
            if (v.code == code) return v;
        }
        throw new IllegalArgumentException("unknown user type: " + code);
    }
}
