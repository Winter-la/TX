package com.thz.spectrum.util;

/**
 * 字符串工具：用于输入清洗与长度截断
 */
public final class Texts {

    private Texts() {}

    /** 去除首尾空白并按最大长度截断；null 原样返回 */
    public static String clip(String s, int max) {
        if (s == null) return null;
        s = s.trim();
        return s.length() > max ? s.substring(0, max) : s;
    }
}
