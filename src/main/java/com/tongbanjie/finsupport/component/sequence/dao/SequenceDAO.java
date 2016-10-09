/*
 * Copyright (C), 2002-2015, 铜板街
 * FileName: SequenceDAO.java
 * Author:   gaoseng(li.yawei)
 * Date:     2015年10月22日 下午4:19:03
 * Description: //模块目的、功能描述      
 * History: //修改记录
 * <author>      <time>      <version>    <desc>
 * 修改人姓名             修改时间            版本号                  描述
 */
package com.tongbanjie.finsupport.component.sequence.dao;

/**
 * 〈一句话功能简述〉<br>
 * 〈功能详细描述〉
 * 
 * @author liyawei
 * @see [相关类/方法]（可选）
 * @since [产品/模块版本] （可选）
 */
public interface SequenceDAO {

    /**
     * 
     * 通过Seq类型查询Seq配置
     * 
     * @param seqName
     * @return
     */
    public SequenceRange queryBySeqNameForUpdate(String seqName);

    /**
     * 更新当前序号
     * 
     * @param seqName
     * @param cur
     * @return
     */
    public int update(String seqName, long cur);

    /**
     * 
     * 新建序号配置
     * 
     * @param sequence
     * @return
     */
    public int insert(SequenceRange sequence);
}
