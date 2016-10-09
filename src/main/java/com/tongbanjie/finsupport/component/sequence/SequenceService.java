/*
 * Copyright (C), 2002-2015, 铜板街
 * FileName: SequenceService.java
 * Author:   gaoseng(li.yawei)
 * Date:     2015年10月22日 下午4:10:57
 * Description: //模块目的、功能描述      
 * History: //修改记录
 * <author>      <time>      <version>    <desc>
 * 修改人姓名             修改时间            版本号                  描述
 */
package com.tongbanjie.finsupport.component.sequence;

import java.util.Map;
import java.util.TreeSet;
import java.util.concurrent.ConcurrentHashMap;

import javax.sql.DataSource;

import org.mybatis.spring.SqlSessionFactoryBean;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.core.io.ClassPathResource;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.TransactionCallback;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.util.Assert;

import com.tongbanjie.finsupport.component.sequence.dao.SequenceDAO;
import com.tongbanjie.finsupport.component.sequence.dao.SequenceRange;
import com.tongbanjie.finsupport.component.sequence.dao.impl.SequenceDAOImpl;

/**
 * 序号生成服务
 * 
 * @author liyawei
 */
public class SequenceService implements InitializingBean, ApplicationContextAware {
    /** 服务端Spring上下文,父上下文为宿主Spring上下文 */
    private static Object              sequenceApplicationContext;
    /** 服务端宿主Spring上下文 */
    private ApplicationContext         applicationContext;
    /** 服务端宿主数据源 */
    private String                     dataSourceName;

    private static SequenceDAO         sequenceDAO;

    private static TransactionTemplate transactionTemplate;

    /** 本地内存存储的序号 */
    private Map<String, SequenceRange> map               = new ConcurrentHashMap<String, SequenceRange>();
    /** 本地内存序号名最大个数 */
    private int                        map_max_size      = 50000;
    /** 本地内存序号名最后使用时间排序-命中率分析 */
    private TreeSet<SequenceRange>     set               = new TreeSet<SequenceRange>();
    /** 命中率分析阀值-本地内存种序号名达到此值，get方法也会添加Sequence到set中 */
    private int                        hit_analysis_size = 40000;

    private long                       default_min       = 1L;
    private long                       default_max       = Long.MAX_VALUE;
    private long                       default_step      = 1L;
    private long                       default_count     = 100L;
    private String                     default_loop      = "N";

    /**
     * 本地内存中获取相对应的Sequence对象，并且在本地内存map大小超过命中率分析阀值时更新命中率分析的set
     * 
     * @param seqName
     * @return
     */
    private SequenceRange get(String seqName) {
        SequenceRange sequenceRange = map.get(seqName);
        if (sequenceRange != null && map.size() > hit_analysis_size) {
            sequenceRange.setLastUpdate(System.currentTimeMillis());
            set.add(sequenceRange);
        }
        return sequenceRange;
    }

    /**
     * 本地内存中设置相对应的Sequence对象，并设置命中率分析的set
     * 
     * @param seqName
     * @param sequenceRange
     * @return
     */
    private SequenceRange put(String seqName, SequenceRange sequenceRange) {
        // 如果本地内存map个数大于最大值，删除最长时间不使用的
        while (map.size() > map_max_size) {
            SequenceRange firstSequenceRange = set.first();
            if (firstSequenceRange == null) {
                throw new RuntimeException("序号生成组件未知异常");
            }
            map.remove(firstSequenceRange.getSeqName());
            set.remove(firstSequenceRange);
        }

        if (sequenceRange != null) {
            sequenceRange.setLastUpdate(System.currentTimeMillis());
            set.add(sequenceRange);
        }
        return map.put(seqName, sequenceRange);
    }

    /**
     * 获取下一个序号ID,推荐使用
     * 
     * @param seqName
     * @return
     */
    public String getNextSeq(String seqName) {
        return getNextSeq(seqName, default_min, default_max, default_step, default_count, false);
    }

    /**
     * 获取下一个序号ID,不推荐使用。除非你必须使用
     * 
     * @param seqName
     * @param startSeq   仅在seqName未存储在数据库中才生效 
     * @return
     * @throws IllegalArgumentException
     */
    public String getNextSeq(String seqName, long startSeq) {
        return getNextSeq(seqName, startSeq, default_max, default_step, default_count, false);
    }

    /**
     * 指定配置获取seq,不推荐使用。除非你必须使用
     *
     * @param seqName
     * @param min      非负    仅在seqName未存储在数据库中才生效
     * @param max      大于min 仅在seqName未存储在数据库中才生效
     * @param step     大于0   仅在seqName未存储在数据库中才生效
     * @param count    大于0   仅在seqName未存储在数据库中才生效
     * @param isLoop          仅在seqName未存储在数据库中才生效
     * @return
     * 
     * @throws IllegalArgumentException
     */
    public String getNextSeq(String seqName, long min, long max, long step, long count,
                             boolean isLoop) {
        Assert.isTrue(min >= 0, "最小值为负");
        Assert.isTrue(max > min, "最大值小于最小值");
        Assert.isTrue(step > 0, "步长为负或0");
        Assert.isTrue(count > 0, "一次性获取个数为负或0");
        if (map.get(seqName) == null) {
            initSequenceRange(seqName, min, max, step, count, isLoop);
        }

        return getSequenceNum(seqName);
    }

    /**
     * 先尝试从本地获取一个序号，如果本地序号用完从数据库捞取一批序号
     * 
     * @param seqName
     * @return
     */
    private String getSequenceNum(final String seqName) {
        final SequenceRange sequenceRange = get(seqName);

        if (sequenceRange == null) {
            throw new RuntimeException("序号生成服务未知异常");
        }
        // 本地已经用完需要取数据库取
        if (sequenceRange.getCur() >= sequenceRange.getMax()) {
            updateFromDBAndUpdateDB(sequenceRange);
        }
        try {
            return sequenceRange.getCurAndIncrementStep();
        } catch (CASEqualsException e) {
            return getSequenceNum(seqName);
        }
    }

    /**
     * 从数据库更新序号范围并更新本地序号范围并更新数据库的cur
     * 
     * @param seqName
     * @param sequenceRange
     */
    private void updateFromDBAndUpdateDB(final SequenceRange sequenceRange) {

        synchronized (sequenceRange) {
            if (sequenceRange.getCur() < sequenceRange.getMax()) {
                return;
            }
            transactionTemplate.execute(new TransactionCallback<SequenceRange>() {

                @Override
                public SequenceRange doInTransaction(TransactionStatus status) {
                    // 锁表并同步本地cur
                    SequenceRange sequenceRangeDB = sequenceDAO
                        .queryBySeqNameForUpdate(sequenceRange.getSeqName());

                    sequenceRange.updateFromDB(sequenceRangeDB);
                    // 本地最大值更新回数据库等待其他客户端获取下一段序号范围
                    sequenceDAO.update(sequenceRange.getSeqName(), sequenceRange.getMax());
                    return sequenceRange;
                }
            });
        }

    }

    /**
     * 初始化本地Sequence，并取一段序号在本地
     * 
     * @param seqName
     * @param isLoop 
     * @param count 
     * @param step 
     * @param max 
     * @param min 
     */
    private synchronized void initSequenceRange(final String seqName, final long min,
                                                final long max, final long step, final long count,
                                                final boolean isLoop) {
        if (get(seqName) != null) {
            return;
        }

        SequenceRange sequenceRange = transactionTemplate
            .execute(new TransactionCallback<SequenceRange>() {

                @Override
                public SequenceRange doInTransaction(TransactionStatus status) {
                    SequenceRange sequenceRangeDB = sequenceDAO.queryBySeqNameForUpdate(seqName);
                    if (sequenceRangeDB == null) {
                        sequenceRangeDB = new SequenceRange();
                        sequenceRangeDB.setCount(count);
                        if (isLoop) {
                            sequenceRangeDB.setLoop("Y");
                        } else {
                            sequenceRangeDB.setLoop(default_loop);
                        }
                        sequenceRangeDB.setMax(max);
                        sequenceRangeDB.setMin(min);
                        sequenceRangeDB.setSeqName(seqName);
                        sequenceRangeDB.setStep(step);
                        sequenceRangeDB.setCur(min);
                        try {
                            sequenceDAO.insert(sequenceRangeDB);
                        } catch (DuplicateKeyException e) {
                            // 和谐掉主键重复异常,重新查询一次
                            sequenceRangeDB = sequenceDAO.queryBySeqNameForUpdate(seqName);
                        }
                    }
                    // 新建内存中的序号范围
                    SequenceRange sequenceRange = new SequenceRange();
                    sequenceRange.setSeqName(seqName);
                    sequenceRange.updateFromDB(sequenceRangeDB);
                    // 本地最大值更新回数据库等待其他客户端获取下一段序号范围
                    sequenceDAO.update(sequenceRange.getSeqName(), sequenceRange.getMax());
                    return sequenceRange;
                }

            });

        put(seqName, sequenceRange);
    }

    /*
     * (non-Javadoc)
     * 
     * @see
     * org.springframework.context.ApplicationContextAware#setApplicationContext
     * (org.springframework.context. ApplicationContext)
     */
    @Override
    public void setApplicationContext(ApplicationContext applicationContext) throws BeansException {
        this.applicationContext = applicationContext;
    }

    /*
     * (non-Javadoc)
     * 
     * @see
     * org.springframework.beans.factory.InitializingBean#afterPropertiesSet()
     */
    @Override
    public void afterPropertiesSet() throws Exception {
        // 服务端初始化一个spring上下文。
        if (sequenceApplicationContext == null) {
            synchronized (SequenceService.class) {
                if (sequenceApplicationContext == null) {

                    DataSource dataSource = this.applicationContext.getBean(dataSourceName,
                        DataSource.class);
                    SqlSessionFactoryBean sequenceSqlSessionFactory = new SqlSessionFactoryBean();
                    sequenceSqlSessionFactory.setDataSource(dataSource);
                    sequenceSqlSessionFactory.setConfigLocation(new ClassPathResource(
                        "mybatis/sequence-configuration.xml"));
                    sequenceSqlSessionFactory
                        .setMapperLocations(new ClassPathResource[] { new ClassPathResource(
                            "mybatis/sequence/sequence.xml") });

                    DataSourceTransactionManager transactionManager = new DataSourceTransactionManager(
                        dataSource);
                    transactionTemplate = new TransactionTemplate(transactionManager);
                    transactionTemplate.setName("sequenceTransaction");
                    transactionTemplate.setPropagationBehaviorName("PROPAGATION_REQUIRES_NEW");

                    sequenceDAO = new SequenceDAOImpl();
                    ((SequenceDAOImpl) sequenceDAO).setSqlSessionFactory(sequenceSqlSessionFactory
                        .getObject());

                    sequenceApplicationContext = new Object();
                }
            }
        }

    }

    /**
     * @param dataSourceName
     *            the dataSourceName to set
     */
    public void setDataSourceName(String dataSourceName) {
        this.dataSourceName = dataSourceName;
    }
}
