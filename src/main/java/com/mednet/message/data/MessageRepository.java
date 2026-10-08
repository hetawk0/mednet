package com.mednet.message.data;

import java.util.List;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MessageRepository extends JpaRepository<MessageEntity, String> {
    Page<MessageEntity> findByConversationIdOrderByCreatedAtAsc(String conversationId, Pageable pageable);

    List<MessageEntity> findTop1ByConversationIdOrderByCreatedAtDesc(String conversationId);
}
