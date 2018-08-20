package com.zxk175.similarity.util;

import com.google.common.collect.Lists;
import com.hankcs.hanlp.HanLP;
import com.hankcs.hanlp.seg.common.Term;
import com.zxk175.similarity.entity.Word;

import java.util.List;

/**
 * @author zxk175
 * @date 2018/8/20 14:26
 */
public class WordSegmentUtils {

    /**
     * 分词
     *
     * @param sentence
     * @return
     */
    public static List<Word> segment(String sentence) {
        // 1、采用HanLP中文自然语言处理中标准分词进行分词
        List<Term> termList = HanLP.segment(sentence);

        // 上面控制台打印信息就是这里输出的
        System.out.println(termList.toString());

        // 2、重新封装到Word对象中（term.word代表分词后的词语，term.nature代表改词的词性）
        List<Word> list = Lists.newArrayList();
        for (Term term : termList) {
            list.add(new Word(term.word, term.nature.toString()));
        }

        return list;
    }
}
