package com.wangguangwu.claim.application.usecase.intake;

import com.wangguangwu.claim.application.domain.claim.ClaimApplicationIntakeSnapshot;
import com.wangguangwu.claim.application.usecase.intake.result.ClaimApplicationIntakeResult;
import java.util.Optional;

/**
 * 签收事务的产物：对外结果与本次回源观察到的可复用快照。
 *
 * <p>快照只在事务内读到 {@code ACCEPTED}、{@code UNDERWRITING}、{@code WAIT_AUDIT} 时才有值，
 * 用例在事务提交后用它把存在标记升级为可复用快照，后续重复请求即可不再读库。</p>
 *
 * @param result           对外签收结果
 * @param reusableSnapshot 回源观察到的可复用快照
 * @author wangguangwu
 * @since 2026-10-30
 */
public record ClaimApplicationIntakeOutcome(
        ClaimApplicationIntakeResult result,
        Optional<ClaimApplicationIntakeSnapshot> reusableSnapshot) {

    /**
     * 不携带可复用快照的产物，用于创建与只写轨迹的场景。
     *
     * @param result 对外签收结果
     * @return 无快照的产物
     */
    public static ClaimApplicationIntakeOutcome withoutSnapshot(ClaimApplicationIntakeResult result) {
        return new ClaimApplicationIntakeOutcome(result, Optional.empty());
    }

    /**
     * 携带可复用快照的产物。
     *
     * @param result   对外签收结果
     * @param snapshot 可复用快照
     * @return 带快照的产物
     */
    public static ClaimApplicationIntakeOutcome withSnapshot(ClaimApplicationIntakeResult result,
                                                             ClaimApplicationIntakeSnapshot snapshot) {
        return new ClaimApplicationIntakeOutcome(result, Optional.of(snapshot));
    }
}
