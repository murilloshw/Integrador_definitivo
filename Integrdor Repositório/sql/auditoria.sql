-- Tabela de auditoria: quem fez o quê e quando.
-- A aplicação cria esta tabela sozinha na primeira gravação (classe Auditoria),
-- mas o script fica aqui para documentar a estrutura e para criação manual.

CREATE TABLE IF NOT EXISTS auditoria (
    ID_Log          BIGINT AUTO_INCREMENT PRIMARY KEY,
    Data_Hora       DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP, -- horário de Brasília (ver Conexao.FUSO_BANCO)
    ID_Funcionario  BIGINT NULL,          -- NULL = ação sem funcionário logado
    Usuario         VARCHAR(50) NULL,     -- login do funcionário no momento da ação
    Acao            VARCHAR(40) NOT NULL, -- ex.: RECARGA, RESGATE, PARTIDA, EXCLUSAO_CLIENTE
    Detalhes        VARCHAR(500) NULL,
    INDEX idx_auditoria_data (Data_Hora),
    INDEX idx_auditoria_acao (Acao)
);

-- Consultas úteis:
-- Últimas 50 ações:
--   SELECT * FROM auditoria ORDER BY Data_Hora DESC LIMIT 50;
-- Tudo o que um funcionário fez hoje:
--   SELECT * FROM auditoria WHERE Usuario = 'admin' AND DATE(Data_Hora) = CURDATE();
-- Todas as exclusões:
--   SELECT * FROM auditoria WHERE Acao LIKE 'EXCLUSAO_%' ORDER BY Data_Hora DESC;
