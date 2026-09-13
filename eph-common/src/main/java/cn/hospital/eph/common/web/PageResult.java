package cn.hospital.eph.common.web;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class PageResult<T> {
    private long page;
    private long size;
    private long total;
    private List<T> list;

    public static <T> PageResult<T> of(long page, long size, long total, List<T> list) {
        return new PageResult<>(page, size, total, list);
    }
}
