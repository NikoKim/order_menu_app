package com.order.menu.module.entertainment.mode.guessstock.credit.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.order.menu.module.entertainment.mode.guessstock.credit.entity.CreditFacility;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

public interface CreditFacilityMapper extends BaseMapper<CreditFacility> {
    @Select("SELECT * FROM guess_stock_credit_facilities WHERE session_id=#{sessionId} AND participant_id=#{participantId} ORDER BY id FOR UPDATE")
    List<CreditFacility> selectAllForUpdate(@Param("sessionId") Long sessionId,
                                            @Param("participantId") Long participantId);

    @Select("SELECT * FROM guess_stock_credit_facilities WHERE session_id=#{sessionId} AND participant_id=#{participantId} AND product_code=#{productCode} LIMIT 1 FOR UPDATE")
    CreditFacility selectProductForUpdate(@Param("sessionId") Long sessionId,
                                          @Param("participantId") Long participantId,
                                          @Param("productCode") String productCode);
}
