
package com.vinsett.budget.command;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ActionRepository extends JpaRepository<CommandAction, UUID> {
    Optional<CommandAction> findByCommandIdAndFingerprint(UUID commandId, String fingerprint);
    List<CommandAction> findByAccountIdAndCommandIdOrderByCreatedAtAscIdAsc(UUID accountId, UUID commandId);
}
