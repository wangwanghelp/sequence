/*
 * Copyright (C), 2002-2015, 铜板街
 * FileName: Sequence.java
 * Author:   gaoseng(li.yawei)
 * Date:     2015年10月22日 下午4:29:52
 * Description: //模块目的、功能描述      
 * History: //修改记录
 * <author>      <time>      <version>    <desc>
 * 修改人姓名             修改时间            版本号                  描述
 */
package com.tongbanjie.finsupport.component.sequence.dao;

import java.util.concurrent.atomic.AtomicLongFieldUpdater;

import com.tongbanjie.finsupport.component.sequence.CASEqualsException;

/**
 * 序号范围
 * <p/>
 * 序号范围会在本地内存中存储和命中率分析，所以设计本类的hashCode和equals均只涉及seqName，排序使用lastUpdate
 * 
 * @author liyawei
 */
public class SequenceRange implements Comparable<SequenceRange> {

    /** 序号类别 */
    private String        seqName;
    /** 当前可用序号 */
    private volatile long cur;
    /** 最大序号(不包含) */
    private long          max;
    /** 最小序号(包含) */
    private long          min;
    /** 序号使用完是否循环使用 Y-循环使用 N-达到最大值报错 */
    private String        loop;
    /** 步长 */
    private long          step;
    /** 一次查询支持的序号数量 */
    private long          count;
    /** 内存中最后更新时间 */
    private long          lastUpdate = System.currentTimeMillis();

    /**
     * @return the seqName
     */
    public String getSeqName() {
        return seqName;
    }

    /**
     * @param seqName
     *            the seqName to set
     */
    public void setSeqName(String seqName) {
        this.seqName = seqName;
    }

    /**
     * @return the cur
     */
    public long getCur() {
        return cur;
    }

    /**
     * @param cur
     *            the cur to set
     */
    public void setCur(long cur) {
        this.cur = cur;
    }

    /**
     * @return the max
     */
    public long getMax() {
        return max;
    }

    /**
     * @param max
     *            the max to set
     */
    public void setMax(long max) {
        this.max = max;
    }

    /**
     * @return the min
     */
    public long getMin() {
        return min;
    }

    /**
     * @param min
     *            the min to set
     */
    public void setMin(long min) {
        this.min = min;
    }

    /**
     * @return the loop
     */
    public String getLoop() {
        return loop;
    }

    /**
     * @param loop
     *            the loop to set
     */
    public void setLoop(String loop) {
        this.loop = loop;
    }

    /**
     * @return the step
     */
    public long getStep() {
        return step;
    }

    /**
     * @param step
     *            the step to set
     */
    public void setStep(long step) {
        this.step = step;
    }

    /**
     * @return the count
     */
    public long getCount() {
        return count;
    }

    /**
     * @param count
     *            the count to set
     */
    public void setCount(long count) {
        this.count = count;
    }

    /*
     * (non-Javadoc)
     * 
     * @see java.lang.Comparable#compareTo(java.lang.Object)
     */
    @Override
    public int compareTo(SequenceRange arg0) {
        if (arg0 == null) {
            return -1;
        }
        if (arg0.getLastUpdate() > this.getLastUpdate()) {
            return -1;
        } else if (arg0.getLastUpdate() == this.getLastUpdate()) {
            return 0;
        } else {
            return 1;
        }
    }

    /*
     * (non-Javadoc)
     * 
     * @see java.lang.Object#hashCode()
     */
    @Override
    public int hashCode() {
        return this.seqName.hashCode();
    }

    /*
     * (non-Javadoc)
     * 
     * @see java.lang.Object#equals(java.lang.Object)
     */
    @Override
    public boolean equals(Object arg0) {
        if (arg0 == null || arg0.getClass() != this.getClass()) {
            return false;
        }
        SequenceRange sequence = (SequenceRange) arg0;
        return this.seqName.equals(sequence.getSeqName());
    }

    /**
     * @return the lastUpdate
     */
    public long getLastUpdate() {
        return lastUpdate;
    }

    /**
     * @param lastUpdate
     *            the lastUpdate to set
     */
    public void setLastUpdate(long lastUpdate) {
        this.lastUpdate = lastUpdate;
    }

    AtomicLongFieldUpdater<SequenceRange> curFieldUpdater = AtomicLongFieldUpdater.newUpdater(
                                                              SequenceRange.class, "cur");

    /**
     * 使用CAS操作来自增一个step
     * 
     * @return
     */
    public String getCurAndIncrementStep() {
        for (;;) {
            // 返回本地cur并cur增加一个步长
            long expect = this.getCur();
            long update;
            if (this.getMax() - this.getCur() > this.getStep()) {
                update = this.getCur() + this.getStep();
            } else {
                update = this.getMax();
            }

            if (expect == update) {
                throw new CASEqualsException();
            }

            if (curFieldUpdater.compareAndSet(this, expect, update)) {
                return String.valueOf(expect);
            }
        }

    }

    /**
     * 从数据库更新本地序号范围，考虑数据库的序号范围已经为最大值和本地序号范围不能溢出
     * <p/>
     * 序号范围的所有配置项均从数据库更新，保证修改数据库后本地内存及时生效
     * 
     * @param sequenceRangeDB
     * @param default_loop
     */
    public void updateFromDB(SequenceRange sequenceRangeDB) {
        if (sequenceRangeDB == null) {
            throw new RuntimeException("序号数据库记录异常!可能数据库记录被删除导致重复序号产生!!");
        }

        this.setCur(sequenceRangeDB.getCur());
        this.setLoop(sequenceRangeDB.getLoop());
        this.setStep(sequenceRangeDB.getStep());
        this.setCount(sequenceRangeDB.getCount());

        // 当前已经到默认的最大值
        if (this.getCur() >= sequenceRangeDB.getMax() && "N".equals(this.getLoop())) {
            throw new RuntimeException("序号已使用完");
        } else if (this.getCur() >= sequenceRangeDB.getMax() && !"N".equals(this.getLoop())) {
            // 从头开始使用
            this.setCur(sequenceRangeDB.getMin());
        }

        // 保证countStep没有溢出而且小于最大值和当前值差额，否则直接把当前到最大值这一段取走
        long countStep = this.getCount() * this.getStep();
        if (sequenceRangeDB.getMax() - this.getCur() > countStep && countStep >= this.getCount()
            && countStep >= this.getStep()) {
            this.setMax(this.getCur() + this.getCount() * this.getStep());
        } else {
            this.setMax(sequenceRangeDB.getMax());
        }
        this.setMin(this.getCur());
    }
}
