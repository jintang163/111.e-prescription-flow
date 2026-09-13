package cn.hospital.eph.transfer.adapter;

public record StockCheckResult(boolean allAvailable, String detail) {
    public static StockCheckResult ok() {
        return new StockCheckResult(true, "库存充足");
    }

    public static StockCheckResult out(String detail) {
        return new StockCheckResult(false, detail);
    }
}
