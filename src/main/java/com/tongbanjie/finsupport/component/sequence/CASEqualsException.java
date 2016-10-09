package com.tongbanjie.finsupport.component.sequence;

/**
 * 做CAS原子更新时，如果期望值和更新值相等，则抛出此异常
 * @author liyawei
 *
 */
public class CASEqualsException extends RuntimeException {

    /**
     * 
     */
    private static final long serialVersionUID = -625517117741251027L;

}
