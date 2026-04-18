package org.example.pmsqueue.event.domain;

/**
 * 이벤트 타입 enum + 타입별 Redis Stream key 매핑.
 *
 * <p>시니어 노트:
 * <ul>
 *   <li>enum 하나로 "이벤트는 이 6종뿐"이라는 사실을 코드 전체에 강제한다.
 *       포스트 스캐폴드 단계에서 신규 타입을 추가하려면 이 enum을 수정해야 하므로,
 *       ADR-0002 정책("신규 기능 금지") 위반이 컴파일 직전에 드러난다.
 *   <li>{@code search_query}와 {@code search_result_click}은 같은 stream("events:search")에
 *       적재되어 단일 consumer가 windowed join을 수행할 수 있어야 한다.
 *   <li>{@code api_error}는 의도적으로 격리된 stream("events:api_error").
 *       에러 폭증 시 분석 이벤트 처리 지연을 방지하기 위한 격벽(bulkhead).
 * </ul>
 */
public enum EventType {

    PAGE_VIEW("events:page_view"),
    CLICK("events:click"),
    SEARCH_QUERY("events:search"),
    SEARCH_RESULT_CLICK("events:search"),
    PURCHASE("events:purchase"),
    API_ERROR("events:api_error");

    private final String streamKey;

    EventType(String streamKey) {
        this.streamKey = streamKey;
    }

    public String streamKey() {
        return streamKey;
    }
}
