package com.wangguangwu.claim.application.application.process;

import com.wangguangwu.claim.application.domain.claim.ClaimApplication;
import com.wangguangwu.claim.application.domain.claim.port.ClaimApplicationRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 问题件下发的数据库事务服务。
 *
 * <p>只负责把已变更的申请聚合在一个独立事务中保存，让用例在保存失败后能够退出原事务重新读取，
 * 避免在 {@code REPEATABLE_READ} 的同一快照里看不到并发赢家。</p>
 *
 * @author wangguangwu
 * @since 2026-11-03
 */
@RequiredArgsConstructor
@Service
public class ClaimApplicationIssueTransactionService {

    private final ClaimApplicationRepository claimApplicationRepository;

    /**
     * 保存申请聚合；返回 false 表示版本已被其他执行者改变。
     *
     * @param claimApplication 已变更的申请聚合
     * @return 保存成功时为 true
     */
    @Transactional
    public boolean save(ClaimApplication claimApplication) {
        return claimApplicationRepository.save(claimApplication);
    }
}
