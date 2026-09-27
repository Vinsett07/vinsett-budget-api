
package com.vinsett.budget.command;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.*;
import java.util.Optional;
import java.util.UUID;

public interface CommandRepository extends JpaRepository<CommandExecution, UUID> {
    Optional<CommandExecution> findByAccountIdAndRequestKey(UUID accountId, UUID requestKey);
    Optional<CommandExecution> findByIdAndAccountId(UUID id, UUID accountId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select c from CommandExecution c where c.id = :id and c.accountId = :accountId")
    Optional<CommandExecution> lock(UUID accountId, UUID id);
}
