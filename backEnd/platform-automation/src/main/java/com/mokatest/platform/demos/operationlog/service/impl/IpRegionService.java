package com.mokatest.platform.demos.operationlog.service.impl;

import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.lionsoul.ip2region.xdb.Searcher;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;

import java.io.InputStream;

/**
 * IP 归属地解析服务（离线，基于 ip2region xdb 数据文件）。
 *
 * 数据文件：resources/ip2region_v4.xdb（约 11MB，启动时全量加载到内存，查询微秒级）。
 * 数据格式：国家|区域|省份|城市|运营商，未知段为 "0"。
 * 精度说明：免费库国内稳定到「市级」，区县级不可靠，不展示。
 */
@Slf4j
@Service
public class IpRegionService {

    private Searcher searcher;

    /**
     * 懒加载检索器：首次使用时加载，避免数据文件缺失时拖垮应用启动
     */
    private synchronized Searcher getSearcher() {
        if (searcher != null) {
            return searcher;
        }
        try (InputStream in = new ClassPathResource("ip2region_v4.xdb").getInputStream()) {
            byte[] content = in.readAllBytes();
            searcher = Searcher.newWithBuffer(content);
            log.info("ip2region 数据文件加载成功（{} bytes）", content.length);
        } catch (Exception e) {
            // 解析失败不阻塞主流程，归属地展示为空即可
            log.error("ip2region 数据文件加载失败，IP 归属地功能不可用: {}", e.getMessage());
        }
        return searcher;
    }

    /**
     * 解析 IP 归属地，返回可直接展示的字符串。
     * 示例：广东省深圳市 / 广东省深圳市·电信 / 内网IP / 未知
     *
     * @param ip IPv4 地址
     * @return 归属地文本；无法解析时返回 null（调用方决定兜底展示）
     */
    public String resolve(String ip) {
        if (ip == null || ip.isEmpty()) {
            return null;
        }
        if (isInternalIp(ip)) {
            return "内网IP";
        }
        // ip2region 2.x 仅支持 IPv4（v6 需单独数据文件），非 IPv4 直接跳过
        if (ip.contains(":")) {
            return null;
        }
        Searcher s = getSearcher();
        if (s == null) {
            return null;
        }
        try {
            // region 格式：国家|区域|省份|城市|运营商
            String region = s.search(ip);
            return format(region);
        } catch (Exception e) {
            log.warn("IP 归属地解析失败 ip={}: {}", ip, e.getMessage());
            return null;
        }
    }

    /**
     * 格式化 ip2region 原始串：中国|0|广东省|深圳市|电信 → 广东省深圳市·电信
     * 国外 IP：美国|0|0|0|0 → 美国
     */
    private String format(String region) {
        if (region == null || region.isEmpty()) {
            return null;
        }
        String[] parts = region.split("\\|");
        StringBuilder sb = new StringBuilder();
        // 国家（"0" 或 "中国" 时不展示国家段，国内记录更简洁）
        if (parts.length > 0 && !"0".equals(parts[0]) && !"中国".equals(parts[0])) {
            sb.append(parts[0]);
        }
        // 省份
        if (parts.length > 2 && !"0".equals(parts[2])) {
            sb.append(parts[2]);
        }
        // 城市（与省份相同时不重复，如直辖市）
        if (parts.length > 3 && !"0".equals(parts[3]) && (parts.length <= 2 || !parts[3].equals(parts[2]))) {
            sb.append(parts[3]);
        }
        // 运营商
        if (parts.length > 4 && !"0".equals(parts[4])) {
            if (sb.length() > 0) {
                sb.append('·');
            }
            sb.append(parts[4]);
        }
        return sb.length() > 0 ? sb.toString() : null;
    }

    private boolean isInternalIp(String ip) {
        return "127.0.0.1".equals(ip)
                || "0:0:0:0:0:0:0:1".equals(ip)
                || "::1".equals(ip)
                || ip.startsWith("10.")
                || ip.startsWith("192.168.")
                || ip.startsWith("169.254.")
                || (ip.startsWith("172.") && isPrivate172(ip));
    }

    /**
     * 172.16.0.0 ~ 172.31.255.255 为私有段
     */
    private boolean isPrivate172(String ip) {
        try {
            int second = Integer.parseInt(ip.split("\\.")[1]);
            return second >= 16 && second <= 31;
        } catch (Exception e) {
            return false;
        }
    }

    @PreDestroy
    public void destroy() {
        if (searcher != null) {
            try {
                searcher.close();
            } catch (Exception ignored) {
            }
        }
    }
}
