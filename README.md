項目願景 廣東話 (Cantonese - Traditional Chinese)
  ROC (Risk Operation Center) 係專為 Web3 機構同交易所（如 HashKey）打造嘅即時鏈上風險監測平台。我哋結合咗 Arkham 同 OkLink 嘅頂尖數據源，為數碼資產安全提供「可視化、智能化、自動化」嘅解決方案。

  核心功能
   1. 全天候鏈上監控: 透過 QuickNode Webhook 即時捕捉以太坊大額交易，秒級識別潛在風險。
   2. 機構級數據集成: 深度接入 Arkham 4-tier 標籤系統，精準識別交易所（Exchange Inflow/Outflow）、混幣器（Mixer）同 OTC 交易。
   3. 智能風險評分: 根據交易行為、標籤權重及資金來源，自動計算風險分值（0-100），快速鎖定 Critical 級別威脅。
   4. 調查時間線與地理映射: 自動生成事故調查時間線（Investigation Timeline），並將資金流向映射至全球金融中心（如 HKG, SIN, NYC），提升合規審查效率。
   5. 即時警報系統: 結合 WebSocket 技術，確保合規團隊第一時間收到「異常交易所流入」或「黑客活動」提醒。

项目愿景 (Mandarin - Simplified Chinese)
  ROC (Risk Operation Center) 是一款面向 Web3 机构及合规交易平台打造的链上威胁情报系统。旨在通过实时数据分析与实体识别技术，帮助交易所降低监管风险，保障用户资产安全。

  核心功能
   1. 实时鲸鱼追踪: 毫秒级监测大额资金流动，支持自定义阈值过滤（如 1000+ ETH）。
   2. 多维实体画像: 集成 Arkham 与 OkLink 智能情报，对钱包地址进行全方位标注（包括交易所热钱包、已知黑客、机构实体）。
   3. 自动化合规响应: 系统自动识别“异常交易所流入”等高风险场景，并触发响应流程，支持团队协作处理案件。
   4. 可视化合规看板: 提供直观的风险仪表盘（Dashboard）和资金流向地理图谱，将复杂的链上数据转化为可决策的商业情报。
   5. 混合风险判定模型: 基于行为分析与静态标签的复合算法，提供动态更新的风险分值，满足反洗钱（AML）及反恐怖融资（CFT）需求。

 English
  Project Vision
  The Risk Operation Center (ROC) is a sophisticated Web3 monitoring solution designed for institutional exchanges and financial entities. It bridges the gap between raw blockchain data and actionable regulatory intelligence, ensuring a
  secure and compliant digital asset ecosystem.

  Core Features
   1. Real-Time Threat Intelligence: Powered by QuickNode webhooks and Web3j, the system monitors live Ethereum transactions to detect anomalies as they happen.
   2. Enterprise Intel Integration: Seamlessly integrates with Arkham Intelligence and OkLink to provide 4-tier address labeling, identifying high-risk entities like Mixers, Darknet markets, and Hackers.
   3. Automated Risk Scoring Engine: A proprietary algorithm that calculates risk scores based on multi-dimensional data points, prioritizing critical incidents for immediate intervention.
   4. Visual Investigation & Timeline: Automatically builds a comprehensive investigation trail for each incident, including an interactive timeline and geographic flow mapping (e.g., mapping OKX/HashKey flows to HKG hubs).
   5. Collaborative Incident Management: Designed for Security Operations Centers (SOC), allowing operators to assign cases, log investigation actions, and resolve threats in real-time.

Tech Stack
   * Backend: Java 17, Spring Boot 3.2.4
   * Blockchain: Web3j, QuickNode (Webhooks)
   * Intelligence: Arkham API, OkLink API
   * Real-time: Spring WebSocket (STOMP), Caffeine Cache
   * Frontend: Thymeleaf, Vanilla CSS (Dashboard & Case Detail UI)
   * Database: MySQL, Spring Data JPA

Why ROC for HashKey?
   * Compliance Ready: Specifically tuned to detect unusual inflows to regulated exchanges.
   * Geo-Aware: Optimized for the Hong Kong/Singapore Web3 hub ecosystem.
   * Scalable Architecture: Built on robust Spring Boot infrastructure ready for enterprise deployment.
