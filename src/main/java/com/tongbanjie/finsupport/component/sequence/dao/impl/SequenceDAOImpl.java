/*
 * Copyright (C), 2002-2015, 铜板街
 * FileName: SequenceDAOImpl.java
 * Author:   gaoseng(li.yawei)
 * Date:     2015年10月22日 下午5:40:09
 * Description: //模块目的、功能描述      
 * History: //修改记录
 * <author>      <time>      <version>    <desc>
 * 修改人姓名             修改时间            版本号                  描述
 */
package com.tongbanjie.finsupport.component.sequence.dao.impl;

import java.util.HashMap;
import java.util.Map;

import org.mybatis.spring.support.SqlSessionDaoSupport;

import com.tongbanjie.finsupport.component.sequence.dao.SequenceDAO;
import com.tongbanjie.finsupport.component.sequence.dao.SequenceRange;

/**
 * 〈一句话功能简述〉<br>
 * 〈功能详细描述〉
 * 
 * @author liyawei
 * @see [相关类/方法]（可选）
 * @since [产品/模块版本] （可选）
 */
public class SequenceDAOImpl extends SqlSessionDaoSupport implements SequenceDAO {

    /*
     * (non-Javadoc)
     * 
     * @see com.tongbanjie.finsupport.component.sequence.dao.SequenceDAO#
     * queryBySeqNameForUpdate(java.lang.String)
     */
    @Override
    public SequenceRange queryBySeqNameForUpdate(String seqName) {
        SequenceRange sequenceRangeDB = (SequenceRange) getSqlSession().selectOne(
            "Sequence.queryBySeqName", seqName);
        if (sequenceRangeDB != null
            && (sequenceRangeDB.getStep() < 1 || sequenceRangeDB.getCount() < 1)) {
            throw new RuntimeException("序号生成配置异常.seqName=" + seqName);
        }
        return sequenceRangeDB;
    }

    /*
     * (non-Javadoc)
     * 
     * @see
     * com.tongbanjie.finsupport.component.sequence.dao.SequenceDAO#update(java
     * .lang.String, long)
     */
    @Override
    public int update(String seqName, long cur) {
        Map<String, Object> param = new HashMap<String, Object>();
        param.put("cur", cur);
        param.put("seqName", seqName);
        return getSqlSession().update("Sequence.update", param);
    }

    /*
     * (non-Javadoc)
     * 
     * @see
     * com.tongbanjie.finsupport.component.sequence.dao.SequenceDAO#insert(com
     * .tongbanjie.finsupport.component.sequence .dao.Sequence)
     */
    @Override
    public int insert(SequenceRange sequence) {
        if (sequence != null && (sequence.getStep() < 1 || sequence.getCount() < 1)) {
            throw new RuntimeException("序号生成配置异常.seqName=" + sequence.getSeqName());
        }
        return getSqlSession().insert("Sequence.insert", sequence);
    }

}
