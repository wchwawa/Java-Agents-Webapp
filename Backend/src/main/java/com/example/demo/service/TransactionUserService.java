package com.example.demo.service;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import com.example.demo.model.Redis.RedisAccount;
import com.example.demo.utility.DtoParser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.redis.core.RedisTemplate;

import org.springframework.security.crypto.password.PasswordEncoder;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.example.demo.repository.TransactionUserDao;
import com.example.demo.exception.UserNotFoundException;
import com.example.demo.model.Account;
import com.example.demo.model.dto.TransactionUserDTO;
import com.example.demo.model.Redis.RedisUser;
import com.example.demo.model.TransactionUser;
import com.example.demo.utility.jwt.JwtUtil;





@Service
public class TransactionUserService {
    private static final Logger logger = LoggerFactory.getLogger(TransactionUserService.class);
    private final TransactionUserDao transactionUserDao;
    private final JwtUtil jwtUtil;
    private final RedisTemplate<String, Object> redisTemplate;

    private final PasswordEncoder passwordEncoder;
    private final DtoParser dtoParser;

    @Autowired
    public TransactionUserService(TransactionUserDao transactionUserDao, JwtUtil jwtUtil, RedisTemplate<String, Object> redisTemplate, PasswordEncoder passwordEncoder, DtoParser dtoParser) {
        this.transactionUserDao = transactionUserDao;
        this.jwtUtil = jwtUtil;
        this.redisTemplate = redisTemplate;
        this.passwordEncoder = passwordEncoder;
        this.dtoParser = dtoParser;
    }

    public List<TransactionUser> findAll() {
        return transactionUserDao.findAll();
    }

    public Optional<TransactionUser> findById(Long id) {
        return transactionUserDao.findById(id);
    }

    public Optional<TransactionUser> findByUsername(String username) {
        return transactionUserDao.findByUsername(username);
    }

    public void updateUser(Long id, TransactionUser updatedUser) throws UserNotFoundException {
        TransactionUser existingUser = findExistingUser(id);

        existingUser.setEmail(updatedUser.getEmail());
        existingUser.setPhone(updatedUser.getPhone());

        // **如果密码不为空，进行加密处理
        updatePasswordIfNotEmpty(updatedUser, existingUser);

        transactionUserDao.save(existingUser);
    }

    // **提取的查找现有用户的方法
    private TransactionUser findExistingUser(Long id) throws UserNotFoundException {
        return transactionUserDao.findById(id)
                .orElseThrow(() -> new UserNotFoundException("User not found"));
    }

    // **提取的更新密码的方法
    private void updatePasswordIfNotEmpty(TransactionUser updatedUser, TransactionUser existingUser) {
        if (updatedUser.getPassword() != null && !updatedUser.getPassword().isEmpty()) {
            String encodedPassword = passwordEncoder.encode(updatedUser.getPassword());
            existingUser.setPassword(encodedPassword);
        }
    }

    public void deleteUser(Long id) throws UserNotFoundException, DataIntegrityViolationException {
        Optional<TransactionUser> userOptional = transactionUserDao.findById(id);

        if (!userOptional.isPresent()) {
            throw new UserNotFoundException("User not found");
        }

        transactionUserDao.deleteById(id);
    }

    public void updateAvatar(String token, String avatar) throws UserNotFoundException {
        token = token.replace("Bearer ", "");
        Long userId = jwtUtil.getUserIdFromToken(token);
        Optional<TransactionUser> userOptional = transactionUserDao.findById(userId);

        if (!userOptional.isPresent()) {
            throw new UserNotFoundException("User not found");
        }

        TransactionUser user = userOptional.get();
        user.setAvatar(avatar);
        transactionUserDao.save(user);
    }


    @Transactional(readOnly = true)
    public Optional<TransactionUserDTO> getUserInfoByUserId(String token) {
        token = token.replace("Bearer ", "");
        Long userId = jwtUtil.getUserIdFromToken(token);
        String userRedisKey = "login_user:" + userId + ":info";


        RedisUser redisUser = (RedisUser) redisTemplate.opsForValue().get(userRedisKey);

        // 如果 Redis 中有用户信息，直接返回
        if (redisUser != null) {
            return Optional.of(getUserInfoFromRedis(redisUser, userId));
        } else {
            return transactionUserDao.findById(userId)
                    .map(this.dtoParser::convertTransactionUserToDTO); // 修改了方法名
        }
    }

    private TransactionUserDTO getUserInfoFromRedis(RedisUser redisUser, Long userId) {
        TransactionUserDTO userDTO = dtoParser.setTransactionUserDTO(redisUser);

        List<String> accountNames = new ArrayList<>();
        String keyPattern = "login_user:" + userId + ":account*";
        Set<String> accountKeys = redisTemplate.keys(keyPattern);
        for (String key : accountKeys){
            if (key.equals("login_user:" + userId + ":account:initial placeholder")){
                continue;
            }
            String accountName = ((RedisAccount) redisTemplate.opsForValue().get(key)).getName();
            accountNames.add(accountName);
        }
        userDTO.setAccountName(accountNames);
        return userDTO;
    }

}