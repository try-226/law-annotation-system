package com.law.annotation.law;

import com.law.annotation.common.exception.ApiException;
import com.law.annotation.common.response.ErrorLocator;
import com.law.annotation.law.dto.LawBaseInfoInput;
import com.law.annotation.law.dto.LawImportArticleInput;
import com.law.annotation.law.dto.LawImportPreviewResponse;
import com.law.annotation.law.dto.LawStructureInput;
import com.law.annotation.law.dto.LawValidationIssue;
import java.time.DateTimeException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

@Service
public class LawTextParser {

    static final int MAX_FULL_TEXT_CODE_POINTS = 500_000;

    private static final String CHINESE_OR_ARABIC_NUMBER =
            "(?:[零〇一二三四五六七八九十百千万两]+|[1-9]\\d*)";
    private static final Pattern ARTICLE_CANDIDATE = Pattern.compile(
            "^\\h*(第[^\\s：:，,。；;]{1,30}?条(?:之[零〇一二三四五六七八九十百千万两1-9\\d]+)?)"
                    + "(?:\\h*[:：]?\\h*)(.*)$");
    private static final Pattern STRUCTURE_CANDIDATE = Pattern.compile(
            "^\\h*(第" + CHINESE_OR_ARABIC_NUMBER + "(编|章|节))\\h*(.*)$");
    private static final Pattern NAME_FIELD = Pattern.compile("^(?:法律名称|名称)\\s*[:：]\\s*(.+)$");
    private static final Pattern AUTHORITY_FIELD = Pattern.compile(
            "^(?:发布机关|制定机关)\\s*[:：]\\s*(.+)$");
    private static final Pattern DATE_FIELD = Pattern.compile(
            "^(?:发布日期|公布日期)\\s*[:：]\\s*(.+)$");
    private static final Pattern ISO_DATE = Pattern.compile("^(\\d{4})-(\\d{1,2})-(\\d{1,2})$");
    private static final Pattern CHINESE_DATE = Pattern.compile("^(\\d{4})年(\\d{1,2})月(\\d{1,2})日$");
    private static final Pattern LIKELY_LAW_TITLE = Pattern.compile(
            "^[《]?[\\p{IsHan}A-Za-z0-9·（）()]{2,100}(?:法|条例|办法|规定|决定)[》]?$");

    public LawImportPreviewResponse parse(String fullTextPaste) {
        String text = validateFullText(fullTextPaste);
        List<String> lines = List.of(text.replace("\r\n", "\n").replace('\r', '\n').split("\n", -1));
        rejectMultipleLaws(lines);

        Metadata metadata = extractMetadata(lines);
        List<MutableStructure> structure = new ArrayList<>();
        List<MutableArticle> articles = new ArrayList<>();
        MutableArticle currentArticle = null;
        MutableStructure currentPart = null;
        MutableStructure currentChapter = null;
        MutableStructure currentSection = null;

        for (String line : lines) {
            String trimmed = line.trim();
            Matcher structureMatcher = STRUCTURE_CANDIDATE.matcher(trimmed);
            Matcher articleMatcher = ARTICLE_CANDIDATE.matcher(trimmed);
            if (structureMatcher.matches()) {
                if (currentArticle != null) {
                    articles.add(currentArticle);
                    currentArticle = null;
                }
                LawStructureNodeType type = typeOf(structureMatcher.group(2));
                String title = (structureMatcher.group(1) + " " + structureMatcher.group(3)).trim();
                String parentId = switch (type) {
                    case PART -> null;
                    case CHAPTER -> currentPart == null ? null : currentPart.nodeId;
                    case SECTION -> currentChapter != null
                            ? currentChapter.nodeId
                            : currentPart == null ? null : currentPart.nodeId;
                };
                MutableStructure node = new MutableStructure(
                        "structure-" + (structure.size() + 1),
                        type,
                        title,
                        parentId,
                        structure.size());
                structure.add(node);
                if (type == LawStructureNodeType.PART) {
                    currentPart = node;
                    currentChapter = null;
                    currentSection = null;
                } else if (type == LawStructureNodeType.CHAPTER) {
                    currentChapter = node;
                    currentSection = null;
                } else {
                    currentSection = node;
                }
                continue;
            }
            if (articleMatcher.matches()) {
                if (currentArticle != null) {
                    articles.add(currentArticle);
                }
                String clientKey = "article-" + (articles.size() + 1);
                currentArticle = new MutableArticle(
                        clientKey,
                        articleMatcher.group(1),
                        articleMatcher.group(2),
                        articles.size());
                MutableStructure owner = currentSection != null
                        ? currentSection
                        : currentChapter != null ? currentChapter : currentPart;
                if (owner != null) {
                    owner.articleRefs.add(clientKey);
                }
                continue;
            }
            if (currentArticle != null) {
                currentArticle.append(line);
            }
        }
        if (currentArticle != null) {
            articles.add(currentArticle);
        }
        if (articles.isEmpty()) {
            throw new ApiException(
                    HttpStatus.UNPROCESSABLE_ENTITY,
                    LawErrorCodes.NO_ARTICLES_DETECTED,
                    "未识别到法条，请确认条号使用“第X条”格式",
                    List.of(new ErrorLocator("fullTextPaste", "未识别到任何法条")));
        }

        List<LawValidationIssue> issues = new ArrayList<>();
        addMetadataIssues(metadata, issues);
        List<LawStructureInput> structurePreview = validateStructurePreview(structure, issues);
        List<LawImportArticleInput> articlePreview = validateArticlePreview(articles, issues);
        List<String> warnings = metadata.unconsumedHeaderLines > 0
                ? List.of("部分正文头部信息未自动识别，请在确认导入前人工核对")
                : List.of();
        return new LawImportPreviewResponse(
                new LawBaseInfoInput(metadata.name, metadata.authority, metadata.publicationDate, null),
                structurePreview,
                articlePreview,
                warnings,
                issues);
    }

    private static String validateFullText(String value) {
        if (value == null || value.isBlank()) {
            throw new ApiException(
                    HttpStatus.UNPROCESSABLE_ENTITY,
                    LawErrorCodes.IMPORT_TEXT_INVALID,
                    "完整法律文本不能为空",
                    List.of(new ErrorLocator("fullTextPaste", "请输入完整法律文本")));
        }
        String withoutBom = value.charAt(0) == '\ufeff' ? value.substring(1) : value;
        int length = withoutBom.codePointCount(0, withoutBom.length());
        if (length > MAX_FULL_TEXT_CODE_POINTS) {
            throw new ApiException(
                    HttpStatus.UNPROCESSABLE_ENTITY,
                    LawErrorCodes.IMPORT_TEXT_INVALID,
                    "粘贴文本不能超过500000个字符，请改用后续支持的文件导入能力",
                    List.of(new ErrorLocator("fullTextPaste", "文本长度超过500000个字符")));
        }
        return withoutBom;
    }

    private static void rejectMultipleLaws(List<String> lines) {
        Set<String> detectedNames = new LinkedHashSet<>();
        for (String line : lines) {
            String trimmed = line.trim();
            Matcher explicitName = NAME_FIELD.matcher(trimmed);
            if (explicitName.matches()) {
                detectedNames.add(explicitName.group(1).trim());
            } else if (LIKELY_LAW_TITLE.matcher(trimmed).matches()) {
                detectedNames.add(trimmed);
            }
        }
        if (detectedNames.size() > 1) {
            throw new ApiException(
                    HttpStatus.UNPROCESSABLE_ENTITY,
                    LawErrorCodes.MULTIPLE_LAWS_DETECTED,
                    "检测到多部法律，请分开录入",
                    List.of(new ErrorLocator("fullTextPaste", "一次只能录入一部法律")));
        }
    }

    private static Metadata extractMetadata(List<String> lines) {
        Metadata metadata = new Metadata();
        boolean reachedContent = false;
        for (String line : lines) {
            String trimmed = line.trim();
            if (trimmed.isEmpty()) {
                continue;
            }
            if (STRUCTURE_CANDIDATE.matcher(trimmed).matches()
                    || ARTICLE_CANDIDATE.matcher(trimmed).matches()) {
                reachedContent = true;
            }
            if (reachedContent) {
                continue;
            }
            Matcher nameMatcher = NAME_FIELD.matcher(trimmed);
            Matcher authorityMatcher = AUTHORITY_FIELD.matcher(trimmed);
            Matcher dateMatcher = DATE_FIELD.matcher(trimmed);
            if (nameMatcher.matches()) {
                metadata.name = nameMatcher.group(1).trim();
            } else if (authorityMatcher.matches()) {
                metadata.authority = authorityMatcher.group(1).trim();
            } else if (dateMatcher.matches()) {
                metadata.publicationDate = parseDate(dateMatcher.group(1).trim());
                if (metadata.publicationDate == null) {
                    metadata.invalidDateValue = dateMatcher.group(1).trim();
                }
            } else if (metadata.name == null && LIKELY_LAW_TITLE.matcher(trimmed).matches()) {
                metadata.name = trimmed;
            } else {
                metadata.unconsumedHeaderLines++;
            }
        }
        return metadata;
    }

    private static LocalDate parseDate(String value) {
        Matcher iso = ISO_DATE.matcher(value);
        Matcher chinese = CHINESE_DATE.matcher(value);
        try {
            if (iso.matches()) {
                return LocalDate.of(
                        Integer.parseInt(iso.group(1)),
                        Integer.parseInt(iso.group(2)),
                        Integer.parseInt(iso.group(3)));
            }
            if (chinese.matches()) {
                return LocalDate.of(
                        Integer.parseInt(chinese.group(1)),
                        Integer.parseInt(chinese.group(2)),
                        Integer.parseInt(chinese.group(3)));
            }
        } catch (DateTimeException ignored) {
            return null;
        }
        return null;
    }

    private static void addMetadataIssues(Metadata metadata, List<LawValidationIssue> issues) {
        if (metadata.name == null) {
            issues.add(issue("IMPORT.MISSING_NAME", "baseInfo.name", null, null, null, "未可靠识别法律名称"));
        }
        if (metadata.authority == null) {
            issues.add(issue(
                    "IMPORT.MISSING_ISSUING_AUTHORITY",
                    "baseInfo.issuingAuthority",
                    null,
                    null,
                    null,
                    "未可靠识别发布机关"));
        }
        if (metadata.publicationDate == null) {
            String message = metadata.invalidDateValue == null
                    ? "未可靠识别发布日期"
                    : "发布日期格式或日期无效：" + metadata.invalidDateValue;
            issues.add(issue(
                    "IMPORT.MISSING_PUBLICATION_DATE",
                    "baseInfo.publicationDate",
                    null,
                    null,
                    null,
                    message));
        }
    }

    private static List<LawStructureInput> validateStructurePreview(
            List<MutableStructure> structures,
            List<LawValidationIssue> issues) {
        List<LawStructureInput> result = new ArrayList<>();
        for (int index = 0; index < structures.size(); index++) {
            MutableStructure node = structures.get(index);
            String title = node.title;
            try {
                title = LawDomainRules.validateStructureTitle(title);
            } catch (IllegalArgumentException exception) {
                issues.add(issue(
                        "IMPORT.INVALID_STRUCTURE_TITLE",
                        "structure[" + index + "].title",
                        null,
                        null,
                        node.title,
                        exception.getMessage()));
            }
            result.add(new LawStructureInput(
                    node.nodeId,
                    node.type,
                    title,
                    node.parentNodeId,
                    node.order,
                    node.articleRefs));
        }
        return result;
    }

    private static List<LawImportArticleInput> validateArticlePreview(
            List<MutableArticle> articles,
            List<LawValidationIssue> issues) {
        List<LawImportArticleInput> result = new ArrayList<>();
        Set<String> seenNumbers = new HashSet<>();
        for (int index = 0; index < articles.size(); index++) {
            MutableArticle article = articles.get(index);
            String number = article.number;
            String body = article.body();
            try {
                number = LawDomainRules.validateArticleNumber(number);
            } catch (IllegalArgumentException exception) {
                issues.add(issue(
                        "IMPORT.INVALID_ARTICLE_NUMBER",
                        "articles[" + index + "].number",
                        index,
                        article.number,
                        null,
                        exception.getMessage()));
            }
            if (!seenNumbers.add(number)) {
                issues.add(issue(
                        "IMPORT.DUPLICATE_ARTICLE_NUMBER",
                        "articles[" + index + "].number",
                        index,
                        number,
                        null,
                        "同一法律内容版本内条号不能重复"));
            }
            try {
                body = LawDomainRules.validateArticleBody(body);
            } catch (IllegalArgumentException exception) {
                issues.add(issue(
                        "IMPORT.INVALID_ARTICLE_BODY",
                        "articles[" + index + "].body",
                        index,
                        number,
                        null,
                        exception.getMessage()));
            }
            result.add(new LawImportArticleInput(article.clientKey, number, body, article.order));
        }
        return result;
    }

    private static LawValidationIssue issue(
            String code,
            String field,
            Integer articleIndex,
            String articleNumber,
            String structurePath,
            String message) {
        return new LawValidationIssue(code, field, articleIndex, articleNumber, structurePath, message);
    }

    private static LawStructureNodeType typeOf(String value) {
        return switch (value) {
            case "编" -> LawStructureNodeType.PART;
            case "章" -> LawStructureNodeType.CHAPTER;
            case "节" -> LawStructureNodeType.SECTION;
            default -> throw new IllegalArgumentException("未知结构节点类型");
        };
    }

    private static final class Metadata {
        private String name;
        private String authority;
        private LocalDate publicationDate;
        private String invalidDateValue;
        private int unconsumedHeaderLines;
    }

    private static final class MutableStructure {
        private final String nodeId;
        private final LawStructureNodeType type;
        private final String title;
        private final String parentNodeId;
        private final int order;
        private final List<String> articleRefs = new ArrayList<>();

        private MutableStructure(
                String nodeId,
                LawStructureNodeType type,
                String title,
                String parentNodeId,
                int order) {
            this.nodeId = nodeId;
            this.type = type;
            this.title = title;
            this.parentNodeId = parentNodeId;
            this.order = order;
        }
    }

    private static final class MutableArticle {
        private final String clientKey;
        private final String number;
        private final int order;
        private final List<String> bodyLines = new ArrayList<>();

        private MutableArticle(String clientKey, String number, String firstBodyLine, int order) {
            this.clientKey = clientKey;
            this.number = number;
            this.order = order;
            bodyLines.add(firstBodyLine == null ? "" : firstBodyLine);
        }

        private void append(String line) {
            bodyLines.add(line);
        }

        private String body() {
            return String.join("\n", bodyLines);
        }
    }
}
