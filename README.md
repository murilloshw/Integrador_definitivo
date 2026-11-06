# Integrador Arcade

Sistema desktop de gestão para um **arcade** (fliperama), feito em **Java Swing** com banco **MySQL**.
Controla clientes, cartões de crédito do arcade, recargas, partidas nas máquinas, resgate de prêmios
com pontos e um programa de incentivo (biblioteca e doação de sangue) que dá créditos aos clientes.

Projeto Integrador.

---

## Funcionalidades

| Tela | O que faz |
|---|---|
| **Login** | Acesso por usuário e senha (hash PBKDF2 + salt). Funcionários inativos são bloqueados. |
| **Início (Dashboard)** | Números do dia, recargas da semana, ranking, máquinas mais jogadas e prêmios acabando. Atualiza sozinho. |
| **Clientes** | Cadastro, edição, busca e exclusão de clientes. |
| **Cartões** | Cartões dos clientes com créditos, pontos e status. |
| **Recargas** | Histórico e nova recarga (R$ 1 = 5 créditos), gravada numa transação junto com o saldo do cartão. Formas de pagamento configuráveis (só admin). |
| **Autoatendimento** | Totem em que o cliente faz a própria recarga (cartão → valor → pagamento → confirmação). Só sai com usuário e senha de administrador (pelo botão de saída ou pelo ESC). |
| **Máquinas** | Cadastro das máquinas de jogo e do valor da partida em créditos. |
| **Partidas** | Histórico de jogadas e simulação de partida (debita créditos e soma pontos). |
| **Prêmios** | Cadastro com foto, vitrine para resgate com pontos (com carrinho) e registro de compras. |
| **Funcionários** | Gestão de funcionários e permissões (só admin). Na aba **Cargos** o admin cadastra os cargos, que aparecem como lista no cadastro de funcionário. |
| **Biblioteca** | Livros, fichas de leitura e doações de livros e de sangue que geram créditos (só bibliotecário). |
| **Log** | "Terminal" em tempo real com tudo o que acontece no sistema: quem fez, o quê e quando (login, cadastros, edições, exclusões, recargas, resgates, partidas...). Só admin. |

### Permissões

- **Administrador (Manager Master)**: campo `Permissão_ADM` do funcionário. Libera Funcionários, Log, Formas de Pagamento e o Autoatendimento.
- **Bibliotecário**: funcionário com cargo "Bibliotecário". Libera a Biblioteca. Esse cargo é protegido: não pode ser excluído nem renomeado.
- **Gerente**: ao escolher esse cargo no cadastro, a permissão de administrador já vem marcada (dá para trocar).

---

## Tecnologias

- Java 17+ (testado no JDK 25)
- Swing (interface)
- MySQL 8 (hospedado no Aiven), acessado pelo driver `mysql-connector-j` 9.7.0

---

## Estrutura

```
Integrdor Repositório/
├── src/                  código-fonte (pacote padrão)
│   ├── TelaLogin.java        ponto de entrada (main)
│   ├── App.java              janela principal e navegação
│   ├── Tela*.java            uma classe por tela
│   ├── Conexao.java          conexão com o MySQL + pool de conexões
│   ├── Funcionario.java      dados do funcionário e hash de senha
│   ├── Sessao.java           quem está logado e as permissões
│   ├── Auditoria.java        registro de quem fez o quê
│   ├── RepositorioCargos.java  cadastro de cargos (SQL)
│   └── images/               ícones e imagens
├── sql/                  scripts do banco (ex.: auditoria.sql)
└── lib/                  dependências (.jar)
```

---

## Como rodar

### 1. Configurar o banco

Copie o arquivo [.env.example](.env.example) para `.env` (na raiz do repositório) e preencha:

```env
DB_URL=jdbc:mysql://SEU_HOST:PORTA/integradorarcade?sslMode=REQUIRED&connectTimeout=10000
DB_USER=seu_usuario
DB_PASSWORD=sua_senha
```

> O `.env` está no `.gitignore`: **nunca** faça commit das credenciais reais.
> As mesmas chaves também podem vir de variáveis de ambiente do sistema.

### 2. Executar

**Pelo VS Code** (com o *Extension Pack for Java*): abra a pasta do repositório e rode a configuração **TelaLogin** (F5).

**Pelo terminal**, dentro de `Integrdor Repositório`:

```powershell
# Windows (PowerShell)
javac -encoding UTF-8 -cp "lib/*" -d bin src/*.java
Copy-Item -Recurse -Force src/images bin/
java -cp "bin;lib/*" TelaLogin
```

```sh
# Linux / Mac (no Git Bash do Windows, troque ":" por ";" no -cp)
javac -encoding UTF-8 -cp "lib/*" -d bin src/*.java
cp -r src/images bin/
java -cp "bin:lib/*" TelaLogin
```

### 3. Primeiro acesso / trocar senha de um usuário

Para definir a senha de um usuário direto pelo terminal:

```sh
java -cp "bin;lib/*" AtualizarSenhaMaster
```

### Atalhos

- **ESC**: pergunta se deseja sair do sistema. No autoatendimento, abre a janela de login de administrador (a mesma do botão de saída).

---

## Auditoria

As ações ficam na tabela `auditoria`, que o sistema cria sozinho na primeira gravação (o script está em
[sql/auditoria.sql](Integrdor%20Repositório/sql/auditoria.sql)). Ações registradas:

`LOGIN`, `LOGIN_FALHOU`, `LOGIN_BLOQUEADO`, `LOGOUT`, `RECARGA`, `RECARGA_AUTOATENDIMENTO`, `PARTIDA`,
`PARTIDA_SEM_SALDO`, `RESGATE`, `AUTOATENDIMENTO_ABERTO`, `AUTOATENDIMENTO_FECHADO`, `APROVACAO_FICHA_LEITURA`,
`DOACAO_LIVRO`, `DOACAO_SANGUE` e `CADASTRO_*` / `EDICAO_*` / `EXCLUSAO_*`
(cliente, cartão, máquina, prêmio, funcionário, cargo, livro, ficha de leitura, forma de pagamento).

Os administradores acompanham tudo pela tela **Log**, ícone de pergaminho na barra lateral, em frases como
"Murilo Pessoa cadastrou o livro › "Dom Casmurro" de Machado de Assis".

```sql
-- últimas 50 ações
SELECT * FROM auditoria ORDER BY Data_Hora DESC LIMIT 50;
```

A gravação é feita em segundo plano e depois da operação principal: se o log falhar, a recarga ou o
resgate **não** é desfeito.

---

## Equipe

- Pedro Pacheco 
- João Nogueira
- Murilo Pessoa
- Entony Brito
