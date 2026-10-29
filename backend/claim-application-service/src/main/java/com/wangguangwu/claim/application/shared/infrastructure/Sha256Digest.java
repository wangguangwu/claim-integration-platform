package com.wangguangwu.claim.application.shared.infrastructure;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

/**
 * 业务标识摘要工具，用于构造不含原始业务值的 Redis Key。
 *
 * @author wangguangwu
 * @since 2026-10-27
 */
public final class Sha256Digest {

    private Sha256Digest() {
    }

    /**
     * 计算字符串的 SHA-256 十六进制摘要。
     *
     * @param value 待摘要文本
     * @return 小写十六进制摘要
     */
    public static String hex(String value) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
            StringBuilder hexadecimal = new StringBuilder(digest.length * 2);
            for (byte currentByte : digest) {
                hexadecimal.append(String.format("%02x", currentByte));
            }
            return hexadecimal.toString();
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("JVM 未提供 SHA-256 算法", exception);
        }
    }
}
