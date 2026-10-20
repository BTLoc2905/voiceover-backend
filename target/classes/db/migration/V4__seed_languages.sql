-- seed ngôn ngữ gồm 16 ngôn ngữ khác nhau
INSERT INTO language (code, name, is_default, is_active, tts_supported) VALUES
    ('vi',    'Tiếng Việt',       TRUE,  TRUE, TRUE), -- tiếng việt là tiếng mặc định
    ('en',    'English',          FALSE, TRUE, TRUE),
    ('ja',    '日本語',            FALSE, TRUE, TRUE),
    ('ko',    '한국어',            FALSE, TRUE, TRUE),
    ('zh-CN', '简体中文',          FALSE, TRUE, TRUE),
    ('fr',    'Français',         FALSE, TRUE, TRUE),
    ('de',    'Deutsch',          FALSE, TRUE, TRUE),
    ('es',    'Español',          FALSE, TRUE, TRUE),
    ('ru',    'Русский',          FALSE, TRUE, TRUE),
    ('th',    'ไทย',              FALSE, TRUE, TRUE),
    ('id',    'Bahasa Indonesia', FALSE, TRUE, TRUE),
    ('it',    'Italiano',         FALSE, TRUE, TRUE),
    ('pt',    'Português',        FALSE, TRUE, TRUE),
    ('ar',    'العربية',          FALSE, TRUE, TRUE),
    ('ms',    'Bahasa Melayu',    FALSE, TRUE, FALSE),
    ('hi',    'हिन्दी',             FALSE, TRUE, FALSE);