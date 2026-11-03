package com.wangguangwu.claim.application.shared.exception;

/**
 * 仓储唯一键冲突信号，与具体持久化技术无关。
 *
 * <p>基础设施实现把数据库唯一约束冲突翻译为本异常，应用层据此执行重读与幂等分类，
 * 不感知 Spring DAO 异常类型。未被业务处理的冲突按系统错误上抛。</p>
 *
 * @author wangguangwu
 * @since 2026-11-03
 */
public class RepositoryConflictException extends RuntimeException {

    /**
     * 使用冲突说明构造异常。
     *
     * @param message 冲突说明
     */
    public RepositoryConflictException(String message) {
        super(message);
    }

    /**
     * 使用冲突说明与原始异常构造异常。
     *
     * @param message 冲突说明
     * @param cause   原始持久化异常
     */
    public RepositoryConflictException(String message, Throwable cause) {
        super(message, cause);
    }
}
