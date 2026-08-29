package com.law.annotation.bootstrap;

import com.law.annotation.common.enums.ValidityStatus;
import com.law.annotation.law.LawStructureNode;
import com.law.annotation.law.LawStructureNodeType;
import com.law.annotation.law.NewArticleDraft;
import java.time.LocalDate;
import java.util.List;

final class DemoSeedData {

    static final String ANNOTATOR_NAME = "演示标注员";
    static final String LAW_NAME = "法律条文标注系统演示办法（非真实法规）";
    static final String ISSUING_AUTHORITY = "法律条文标注系统演示项目组（非真实机关）";
    static final LocalDate PUBLICATION_DATE = LocalDate.of(2026, 8, 19);

    private static final List<NewArticleDraft> ARTICLES = List.of(
            article(1, "第一条", "本办法仅用于法律条文标注系统的课程演示和功能验证，不是真实法规，不产生任何法律效力。"),
            article(2, "第二条", "演示数据用于展示法律导入、任务创建、标注、审核和修订等系统操作。"),
            article(3, "第三条", "使用演示数据时，应当避免录入真实个人信息、真实案件材料或者其他敏感内容。"),
            article(4, "第四条", "演示环境中的账号、密码和数据不得直接用于生产环境。"),
            article(5, "第五条", "系统管理员负责配置演示环境，并确认首次管理员初始化已经完成。"),
            article(6, "第六条", "演示标注员可以在被分配任务后保存草稿，并按既定流程提交审核。"),
            article(7, "第七条", "演示环境的清空、恢复和备份操作应当遵守部署文档中的安全提示。"),
            article(8, "第八条", "本办法所述内容全部为虚构示例，仅供软件测试和教学使用。"));

    private static final List<LawStructureNode> STRUCTURE = List.of(
            new LawStructureNode(
                    "demo-chapter-1",
                    LawStructureNodeType.CHAPTER,
                    "第一章 总则",
                    null,
                    0,
                    List.of(articleId(1), articleId(2), articleId(3), articleId(4))),
            new LawStructureNode(
                    "demo-chapter-2",
                    LawStructureNodeType.CHAPTER,
                    "第二章 演示管理",
                    null,
                    1,
                    List.of(articleId(5), articleId(6), articleId(7), articleId(8))));

    private DemoSeedData() {
    }

    static List<NewArticleDraft> articles() {
        return ARTICLES;
    }

    static List<LawStructureNode> structure() {
        return STRUCTURE;
    }

    static ValidityStatus validityStatus() {
        return ValidityStatus.ACTIVE;
    }

    private static NewArticleDraft article(int index, String number, String body) {
        return new NewArticleDraft(articleId(index), number, body, index - 1);
    }

    private static String articleId(int index) {
        return "demo-article-" + index;
    }
}
