package com.zxk175.similarity.util;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.StrUtil;
import com.google.common.collect.Maps;
import com.zxk175.similarity.entity.Word;

import java.math.BigDecimal;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import java.util.function.Function;

/**
 * 判定方式：余弦相似度，通过计算两个向量的夹角余弦值来评估他们的相似度
 * 余弦夹角原理：
 * 向量a=(x1,y1),向量b=(x2,y2) similarity=a.b/|a|*|b| a.b=x1x2+y1y2
 * |a|=根号[(x1)^2+(y1)^2],|b|=根号[(x2)^2+(y2)^2]
 *
 * @author zxk175
 * @date 2018/8/20 14:37
 */
public class CosineSimilarity {

    /**
     * 1、计算两个字符串的相似度
     *
     * @param text1
     * @param text2
     * @return
     */
    public static double getSimilarity(String text1, String text2) {
        // 如果为空，或者字符长度为0，则代表完全相同
        if (StrUtil.isBlank(text1) && StrUtil.isBlank(text2)) {
            return 1.0;
        }

        // 如果一个为0或者空，一个不为，那说明完全不相似
        if (StrUtil.isBlank(text1) || StrUtil.isBlank(text2)) {
            return 0.0;
        }

        // 这个代表如果两个字符串相等那当然返回1了（这个我为了让它也分词计算一下，所以注释掉了）
        if (text1.equalsIgnoreCase(text2)) {
            return 1.0;
        }

        // 第一步：进行分词
        List<Word> words1 = WordSegmentUtils.segment(text1);
        List<Word> words2 = WordSegmentUtils.segment(text2);

        return getSimilarity(words1, words2);
    }

    /**
     * 2、对于计算出的相似度保留小数点后六位
     *
     * @param words1
     * @param words2
     * @return
     */
    public static double getSimilarity(List<Word> words1, List<Word> words2) {
        double score = getSimilarityImpl(words1, words2);

        // (int) (score * 1000000 + 0.5)其实代表保留小数点后6位
        // 因为1034234.213强制转换不就是1034234。对于强制转换添加0.5就等于四舍五入
        score = (int) (score * 1000000 + 0.5) / (double) 1000000;

        return score;
    }

    /**
     * 文本相似度计算 判定方式：余弦相似度，通过计算两个向量的夹角余弦值来评估他们的相似度
     * 余弦夹角原理：
     * 向量a=(x1,y1),向量b=(x2,y2) similarity=a.b/|a|*|b| a.b=x1x2+y1y2
     * |a|=根号[(x1)^2+(y1)^2],|b|=根号[(x2)^2+(y2)^2]
     *
     * @param words1
     * @param words2
     * @return
     */
    public static double getSimilarityImpl(List<Word> words1, List<Word> words2) {
        // 向每一个Word对象的属性都注入weight（权重）属性值
        taggingWeightByFrequency(words1, words2);

        // 第二步：计算词频
        // 通过上一步让每个Word对象都有权重值，那么在封装到map中（key是词，value是该词出现的次数（即权重））
        Map<String, Float> weightMap1 = getFastSearchMap(words1);
        Map<String, Float> weightMap2 = getFastSearchMap(words2);

        // 将所有词都装入set容器中
        Set<Word> words = new HashSet<>();
        words.addAll(words1);
        words.addAll(words2);

        // a.b
        AtomicFloat ab = new AtomicFloat();
        // |a|的平方
        AtomicFloat aa = new AtomicFloat();
        // |b|的平方
        AtomicFloat bb = new AtomicFloat();

        // 第三步：写出词频向量，后进行计算
        words.parallelStream().forEach(new Consumer<Word>() {
            @Override
            public void accept(Word word) {
                // 看同一词在a、b两个集合出现的此次
                Float x1 = weightMap1.get(word.getName());
                Float x2 = weightMap2.get(word.getName());
                if (x1 != null && x2 != null) {
                    // x1x2
                    float oneOfTheDimension = x1 * x2;
                    // +
                    ab.addAndGet(oneOfTheDimension);
                }
                if (x1 != null) {
                    // (x1)^2
                    float oneOfTheDimension = x1 * x1;
                    // +
                    aa.addAndGet(oneOfTheDimension);
                }
                if (x2 != null) {
                    // (x2)^2
                    float oneOfTheDimension = x2 * x2;
                    // +
                    bb.addAndGet(oneOfTheDimension);
                }
            }
        });

        // |a| 对aa开方
        double aaa = Math.sqrt(aa.doubleValue());
        // |b| 对bb开方
        double bbb = Math.sqrt(bb.doubleValue());

        // 使用BigDecimal保证精确计算浮点数
        BigDecimal result = BigDecimal.valueOf(aaa).multiply(BigDecimal.valueOf(bbb));

        // similarity=a.b/|a|*|b|
        // divide参数说明：aabb被除数,9表示小数点后保留9位，最后一个表示用标准的四舍五入法
        return BigDecimal.valueOf(ab.get()).divide(result, 9, BigDecimal.ROUND_HALF_UP).doubleValue();
    }

    /**
     * 向每一个Word对象的属性都注入weight（权重）属性值
     *
     * @param words1
     * @param words2
     */
    protected static void taggingWeightByFrequency(List<Word> words1, List<Word> words2) {
        if (words1.get(0).getWeight() != null && words2.get(0).getWeight() != null) {
            return;
        }

        // 词频统计（key是词，value是该词在这段句子中出现的次数）
        Map<String, AtomicInteger> frequency1 = getFrequency(words1);
        Map<String, AtomicInteger> frequency2 = getFrequency(words2);

        System.out.println("词频统计1：\n" + getWordsFrequencyString(frequency1));
        System.out.println("词频统计2：\n" + getWordsFrequencyString(frequency2));

        // 标注权重（该词出现的次数）
        words1.parallelStream().forEach(word -> word.setWeight(frequency1.get(word.getName()).floatValue()));
        words2.parallelStream().forEach(word -> word.setWeight(frequency2.get(word.getName()).floatValue()));
    }

    /**
     * 统计词频
     *
     * @param words
     * @return 词频统计图
     */
    private static Map<String, AtomicInteger> getFrequency(List<Word> words) {
        Map<String, AtomicInteger> freq = Maps.newHashMap();
        // 这步很帅哦
        for (Word i : words) {
            freq.computeIfAbsent(i.getName(), new Function<String, AtomicInteger>() {
                @Override
                public AtomicInteger apply(String k) {
                    return new AtomicInteger();
                }
            }).incrementAndGet();
        }
        return freq;
    }

    /**
     * 输出：词频统计信息
     *
     * @param frequency
     * @return
     */
    private static String getWordsFrequencyString(Map<String, AtomicInteger> frequency) {
        StringBuilder str = new StringBuilder();
        if (CollUtil.isNotEmpty(frequency)) {
            AtomicInteger integer = new AtomicInteger();
            List<Map.Entry<String, AtomicInteger>> toSort = new ArrayList<>();
            for (Map.Entry<String, AtomicInteger> i : frequency.entrySet()) {
                toSort.add(i);
            }

            toSort.sort(new Comparator<Map.Entry<String, AtomicInteger>>() {
                @Override
                public int compare(Map.Entry<String, AtomicInteger> a, Map.Entry<String, AtomicInteger> b) {
                    return b.getValue().get() - a.getValue().get();
                }
            });
            for (Map.Entry<String, AtomicInteger> i : toSort) {
                str.append("\t")
                        .append(integer.incrementAndGet())
                        .append("、")
                        .append(i.getKey())
                        .append("=")
                        .append(i.getValue())
                        .append("\n");
            }
        }
        str.setLength(str.length() - 1);
        return str.toString();
    }

    /**
     * 构造权重快速搜索容器
     *
     * @param words
     * @return
     */
    protected static Map<String, Float> getFastSearchMap(List<Word> words) {
        if (CollUtil.isEmpty(words)) {
            return Maps.newConcurrentMap();
        }
        Map<String, Float> weightMap = new ConcurrentHashMap<>(words.size());

        words.parallelStream().forEach(new Consumer<Word>() {
            @Override
            public void accept(Word i) {
                if (i.getWeight() != null) {
                    weightMap.put(i.getName(), i.getWeight());
                } else {
                    System.out.println("no word weight info:" + i.getName());
                }
            }
        });

        return weightMap;
    }
}
