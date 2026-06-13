package com.health.repository;

import com.health.entity.AIChatMessage;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface AIChatMessageRepository extends JpaRepository<AIChatMessage, Long> {
    
    // 根据用户ID获取聊天历史
    List<AIChatMessage> findByUserIdOrderByTimestampDesc(Long userId);
    
    // 根据用户ID和聊天ID获取聊天记录
    List<AIChatMessage> findByUserIdAndChatIdOrderByTimestampAsc(Long userId, String chatId);

    // 删除指定会话
    void deleteByUserIdAndChatId(Long userId, String chatId);
    
    // 删除用户的所有聊天记录
    void deleteByUserId(Long userId);
}
