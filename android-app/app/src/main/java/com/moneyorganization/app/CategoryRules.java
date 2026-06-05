package com.moneyorganization.app;

import java.util.LinkedHashMap;
import java.util.Map;

public final class CategoryRules {
    private static final Map<String, String[]> RULES = new LinkedHashMap<>();

    static {
        RULES.put("餐饮", new String[]{"餐", "饭", "咖啡", "奶茶", "瑞幸", "星巴克", "美团", "饿了么", "肯德基", "麦当劳", "食堂", "便利店"});
        RULES.put("购物", new String[]{"淘宝", "天猫", "京东", "拼多多", "抖音商城", "得物", "唯品会", "商场", "优衣库", "购物"});
        RULES.put("学习", new String[]{"书", "课程", "学费", "考试", "教育", "文具", "打印", "教材"});
        RULES.put("交通", new String[]{"地铁", "公交", "滴滴", "高德", "铁路", "12306", "火车", "机票", "打车", "停车", "加油"});
        RULES.put("居住", new String[]{"房租", "物业", "水费", "电费", "燃气", "宽带", "家政", "维修"});
        RULES.put("娱乐", new String[]{"电影", "影院", "游戏", "音乐", "会员", "腾讯视频", "爱奇艺", "优酷", "演出", "ktv"});
        RULES.put("医疗", new String[]{"医院", "药", "诊所", "体检", "医保", "健康"});
        RULES.put("人情", new String[]{"红包", "转账", "礼物", "份子", "亲属卡"});
        RULES.put("通讯", new String[]{"话费", "移动", "联通", "电信", "流量"});
    }

    private CategoryRules() {
    }

    public static String classify(String text) {
        String target = text == null ? "" : text.toLowerCase();
        for (Map.Entry<String, String[]> entry : RULES.entrySet()) {
            for (String keyword : entry.getValue()) {
                if (target.contains(keyword.toLowerCase())) {
                    return entry.getKey();
                }
            }
        }
        return "其他";
    }
}
