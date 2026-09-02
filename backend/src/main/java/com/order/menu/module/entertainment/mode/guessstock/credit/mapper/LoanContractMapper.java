package com.order.menu.module.entertainment.mode.guessstock.credit.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.order.menu.module.entertainment.mode.guessstock.credit.entity.LoanContract;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

public interface LoanContractMapper extends BaseMapper<LoanContract> {
    @Select("SELECT * FROM guess_stock_loan_contracts WHERE id=#{id} FOR UPDATE")
    LoanContract selectByIdForUpdate(@Param("id") Long id);
}
