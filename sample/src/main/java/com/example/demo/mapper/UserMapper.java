package com.example.demo.mapper;

import com.example.demo.entity.User;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;
import java.util.Optional;

@Mapper
public interface UserMapper {

    List<User> findAll();

    Optional<User> findById(@Param("id") Long id);

    List<User> findByStatus(@Param("status") int status);

    List<User> search(@Param("keyword") String keyword,
                      @Param("status")  int status,
                      @Param("role")    String role);

    int insert(User user);

    int update(User user);

    int deleteById(@Param("id") Long id);

    int countByEmail(@Param("email") String email);
}
