package com.djh.researchops.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface BusinessCodeSequenceMapper {

    @Select("SELECT current_value FROM business_code_sequence WHERE entity_type = #{entityType} FOR UPDATE")
    Long lockCurrentValue(@Param("entityType") String entityType);

    @Update("UPDATE business_code_sequence SET current_value = #{nextValue} WHERE entity_type = #{entityType} AND current_value = #{currentValue}")
    int advance(@Param("entityType") String entityType, @Param("currentValue") long currentValue,
                @Param("nextValue") long nextValue);
}
