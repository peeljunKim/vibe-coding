/* 기사 HTML 구조 진단 검증 */
package com.newsverification.article.infrastructure;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/** 원문 없는 날짜와 본문 구조 진단 */
class ArticleHtmlStructureDiagnosticsTest {

    /** 날짜 Metadata와 본문 후보 및 구조화 필드 식별 */
    @Test
    void reportsStructureWithoutArticleText() {
        String html = """
                <html lang="ko"><head>
                <meta name="publish-date" content="2026-10-07T09:30:00+09:00">
                <script type="application/ld+json">
                {"@type":"NewsArticle","headline":"기사 제목","datePublished":"2026-10-07","articleBody":"기사 전문"}
                </script>
                </head><body>
                <div id="storyShell" class="story-content">
                    문단 태그 없이 제공되는 기사 본문 구조를 확인하기 위한 충분히 긴 진단용 텍스트입니다.
                </div>
                <div id="articleBody" class="news-content">
                    <p>첫 번째 기사 문단입니다.</p>
                    <p>두 번째 기사 문단입니다.</p>
                </div>
                </body></html>
                """;

        var diagnostics = ArticleHtmlStructureDiagnostics.inspect(html);

        assertThat(diagnostics.dateSources())
                .containsExactly("meta[name=publish-date]");
        assertThat(diagnostics.bodyCandidates())
                .contains(
                        "div#articleBody.news-content[p=2,text=29]",
                        "div#storyShell.story-content[p=0,text=49]"
                );
        assertThat(diagnostics.structuredFields())
                .containsExactly(
                        "json=valid",
                        "@type=text",
                        "NewsArticle",
                        "headline",
                        "datePublished",
                        "datePublished=2026-10-07",
                        "articleBody"
                );
        assertThat(diagnostics.toString()).doesNotContain("기사 전문", "첫 번째 기사 문단");
    }
}
