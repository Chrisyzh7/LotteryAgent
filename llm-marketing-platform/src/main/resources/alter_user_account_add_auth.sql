-- 新增 username 和 password 字段，用于支持本地注册与登录
ALTER TABLE `user_account`
    ADD COLUMN `username` varchar(64) NULL COMMENT '登录账号' AFTER `id`,
    ADD COLUMN `password` varchar(255) NULL COMMENT '加密后的登录密码' AFTER `username`,
    ADD UNIQUE INDEX `uq_username`(`username`);
