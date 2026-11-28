package com.doc.service;

import com.doc.config.AppProperties;
import com.doc.dto.*;
import jakarta.validation.constraints.NotBlank;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.ai.vectorstore.filter.FilterExpressionBuilder;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Handles document-based question answering using RAG.
 * Retrieves relevant document chunks, builds context, and generates
 * AI responses with conversation memory support.
 * Supports both synchronous and streaming responses.
 */
@Service
@RequiredArgsConstructor
public class RagService {
    private static final Logger log = LoggerFactory.getLogger(RagService.class);

    private final VectorStore vectorStore;
    private final AppProperties appProperties;
    private final ChatClient chatClient;

    //to ask anything related to document
    public ChatResponseDto askQuestion(ChatRequestDto request){

        long startTime = System.currentTimeMillis();
        log.info("Processing query: '{}', scoped documentId: {}", request.getQuestion(), request.getDocumentId());

        List<Document> similarDocuments = this.retrieveRelevantDocuments(
                request.getQuestion(),
                request.getDocumentId(),
                request.getTopK(),
                request.getMinSimilarity()

        );

        List<CitationDto> citationDtos = similarDocuments.stream().map(this::mapToCitation).toList();

        String contextText = buildContextString(similarDocuments);

        //you have to use conversationId to remember the conversation
        //ChatMemory
        //ChatMemoryRepository
        String answer = this.chatClient.prompt()
                          .system(s -> s.param("doc_context",contextText))
                          .user(request.getQuestion())
                .advisors(a -> a.param(ChatMemory.CONVERSATION_ID,request.getConversationId()))
                .call().content();

        long responseTime = System.currentTimeMillis() - startTime;
        log.info("Completed Q&A in {} ms with {} citations", responseTime, citationDtos.size());
        return ChatResponseDto.builder().answer(answer).conversationId(request.getConversationId() != null ? request.getConversationId() : UUID.randomUUID().toString()).citations(citationDtos).responseTimeMs(responseTime).build();
    }

    //this is for stream
    public Flux<String> streamQuestionAnswer(ChatRequestDto requestDto){

        log.info("Streaming query: '{}'", requestDto.getQuestion());

        List<Document> similarDocuments = this.retrieveRelevantDocuments(
                requestDto.getQuestion(),
                requestDto.getDocumentId(),
                requestDto.getTopK(),
                requestDto.getMinSimilarity()

        );

        List<CitationDto> citationDtos = similarDocuments.stream().map(this::mapToCitation).toList();

        String contextText = buildContextString(similarDocuments);

        return chatClient.prompt()
                .system(s-> s.param("doc_context",contextText))
                .user(requestDto.getQuestion())
                .advisors(a -> a.param(ChatMemory.CONVERSATION_ID, requestDto.getConversationId()))
                .stream()
                .content()
                .concatWith(Flux.just("[DONE]"));

    }

    public SearchResultDto searchSimilarChunks(SearchRequestDto request) {

        List<Document> matchedDocs = retrieveRelevantDocuments(request.getQuery(), request.getDocumentId(), request.getTopK(), request.getSimilaritySearch());

        List<CitationDto> citations = matchedDocs.stream().map(this::mapToCitation).toList();
        return SearchResultDto.builder().query(request.getQuery()).totalMatches(citations.size()).matches(citations).build();

    }

    private CitationDto mapToCitation(Document document) {

        Map<String, Object> meta = document.getMetadata();
        UUID docId = null;
        if (meta.get("documentId") != null) {
            try {
                docId = UUID.fromString(meta.get("documentId").toString());
            } catch (Exception ignore) {

            }
        }
        Integer chunkIndex = null;
        if (meta.get("chunkIndex") instanceof Number n) {
            chunkIndex = n.intValue();
        }

        Integer pageNumber = null;
        if (meta.get("pageNumber") instanceof Number n) {
            pageNumber = n.intValue();
        } else if (meta.get("page_number") instanceof Number n) {
            pageNumber = n.intValue();
        }

        Double score = null;
        if (meta.get("distance") instanceof Number n) {
            score = 1.0 - n.doubleValue();
        }
        return CitationDto.builder().documentId(docId).fileName((String) meta.getOrDefault("fileName", "Unknown")).chunkIndex(chunkIndex).pageNumber(pageNumber).snippet(document.getText()).similarityScore(score).metadata(meta).build();
    }

    //Generation of Context String
    private String buildContextString(List<Document> similarDocuments) {
        if (similarDocuments == null || similarDocuments.isEmpty()) {
            return "";
        }

        return similarDocuments.stream().map(doc -> {
            String fileName = (String) doc.getMetadata().getOrDefault("fileName", "unknown file");
            Object page = doc.getMetadata().getOrDefault("pageNumber", "N/A");
            return String.format("[Source: %s | Page: %s]\n%s", fileName, page, doc.getText());
        }).collect(Collectors.joining("\n\n---\n\n"));

    }

    //    this is very important document that fetches the similar result from vector db
    private List<Document> retrieveRelevantDocuments(@NotBlank(message = "Query cannot be empty") String query, UUID documentId, Integer topK, Double similaritySearch) {

        int effectiveTopK = (topK != null && topK > 0) ? topK : appProperties.getRag().getTopK();
        double effectiveSimilarity = (similaritySearch != null) ? similaritySearch : appProperties.getRag().getSimilarityThreshold();

        SearchRequest.Builder searchRequestBuilder = SearchRequest.builder()
                .query(query)
                .topK(effectiveTopK);

        if (effectiveSimilarity > 0.0) {
            searchRequestBuilder.similarityThreshold(effectiveSimilarity);
        }

        if (documentId != null) {

            FilterExpressionBuilder b = new FilterExpressionBuilder();
            searchRequestBuilder.filterExpression(b.eq("documentId", documentId.toString()).build());
        }

        try {
            List<Document> documents = vectorStore.similaritySearch(searchRequestBuilder.build());
            log.info("Retrieved {} chunks for query: '{}' (scoped docId: {})", documents.size(), query, documentId);
            return documents;
        } catch (Exception e) {
            log.error("Similarity search failed for query: '{}'", query, e);
            return Collections.emptyList();
        }

    }
}
