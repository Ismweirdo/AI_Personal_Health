import request from '../utils/request';

export interface AIChatMessage {
  id: string;
  role: 'user' | 'assistant';
  content: string;
  timestamp: string;
  chatId: string;
}

export interface AIChatRequest {
  message: string;
  chatId: string;
  context?: AIChatMessage[];
}

export interface AIProviderInfo {
  code: string;
  name: string;
}

export interface AIChatSession {
  chatId: string;
  title: string;
  preview: string;
  updatedAt: string;
  messageCount: number;
}

export interface AIRecommendedQuestion {
  question: string;
  category: string;
  reason: string;
}

export interface AIActionContextSnapshot {
  healthRecordCount: number;
  activeGoalCount: number;
  reminderCount: number;
  enabledReminderCount: number;
  unreadNotificationCount: number;
  deviceCount: number;
  latestMetricSummaries: string[];
}

export interface AIGoalDraft {
  type: string;
  typeLabel: string;
  targetValue: number;
  unit?: string;
  period: 'daily' | 'weekly' | 'monthly';
  enabled?: boolean;
  currentValue?: number;
  suggestionReason?: string;
}

export interface AIReminderDraft {
  title: string;
  type?: string;
  message?: string;
  frequency: 'daily' | 'weekly' | 'once';
  remindTime?: string;
  remindDate?: string;
  weeklyDay?: number;
  enabled?: boolean;
  suggestionReason?: string;
}

export interface AIActionDraftResponse {
  instruction: string;
  summary: string;
  contextSnapshot: AIActionContextSnapshot;
  goalDraft?: AIGoalDraft;
  reminderDraft?: AIReminderDraft;
  warnings: string[];
}

export interface SwitchProviderResponse {
  success: boolean;
  provider: string;
  message: string;
  error?: string;
}

export async function getAIResponse(req: AIChatRequest): Promise<any> {
  return request.post('/ai/chat', req);
}

export async function getChatHistory(chatId?: string): Promise<AIChatMessage[]> {
  return request.get('/ai/history', { params: chatId ? { chatId } : undefined });
}

export async function getChatSessions(): Promise<AIChatSession[]> {
  return request.get('/ai/sessions');
}

export async function getRecommendedQuestions(): Promise<AIRecommendedQuestion[]> {
  return request.get('/ai/recommended-questions');
}

export async function clearChatHistory(chatId?: string): Promise<void> {
  return request.delete('/ai/history', { params: chatId ? { chatId } : undefined });
}

export async function getCurrentProvider(): Promise<AIProviderInfo> {
  return request.get('/ai/provider/current');
}

export async function getAvailableProviders(): Promise<AIProviderInfo[]> {
  return request.get('/ai/provider/available');
}

export async function switchProvider(provider: string): Promise<SwitchProviderResponse> {
  return request.post('/ai/provider/switch', { provider });
}

export async function generateAIActionDraft(instruction: string): Promise<AIActionDraftResponse> {
  return request.post('/ai/assistant/draft', { instruction });
}

export async function getAIStreamResponse(
  req: AIChatRequest,
  onChunk: (chunk: string) => void,
  onComplete?: () => void,
  onError?: (error: Error) => void
): Promise<void> {
  try {
    const response = await fetch('/api/ai/chat/stream', {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
        'Authorization': `Bearer ${localStorage.getItem('token')}`
      },
      body: JSON.stringify(req)
    });

    if (!response.ok) {
      throw new Error(`HTTP error! status: ${response.status}`);
    }

    const reader = response.body?.getReader();
    if (!reader) {
      throw new Error('No response body');
    }

    const decoder = new TextDecoder('utf-8');
    let buffer = '';
    let currentEvent = '';

    for (;;) {
      const { done, value } = await reader.read();
      
      if (done) {
        break;
      }

      buffer += decoder.decode(value, { stream: true });
      
      const lines = buffer.split('\n');
      buffer = lines.pop() || '';

      for (const line of lines) {
        if (line.startsWith('event:')) {
          currentEvent = line.replace('event:', '').trim();
        } else if (line.startsWith('data:')) {
          const data = line.replace('data:', '').trim();
          if (currentEvent === 'message' && data) {
            onChunk(data);
          } else if (currentEvent === 'complete') {
            onComplete?.();
          }
        }
      }
    }

    onComplete?.();
  } catch (error) {
    onError?.(error as Error);
    throw error;
  }
}
