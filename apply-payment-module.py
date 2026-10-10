#!/usr/bin/env python3
from pathlib import Path
root=Path(__file__).resolve().parent
pom=root/'pom.xml'
s=pom.read_text()
if '<module>payment-service</module>' not in s:
 s=s.replace('<module>inventory-service</module>','<module>inventory-service</module>\n        <module>payment-service</module>')
 pom.write_text(s)
compose=root/'docker-compose.yml'
s=compose.read_text()
if '  payment-db:' not in s:
 anchor='  kafka:\n'
 block='  payment-db:\n    image: postgres:17\n    container_name: commerce-payment-db\n    environment:\n      POSTGRES_DB: payments\n      POSTGRES_USER: payment_user\n      POSTGRES_PASSWORD: payment_password\n    ports:\n      - "5435:5432"\n    volumes:\n      - payment-db-data:/var/lib/postgresql/data\n    healthcheck:\n      test: ["CMD-SHELL", "pg_isready -U payment_user -d payments"]\n      interval: 5s\n      timeout: 5s\n      retries: 5\n\n'
 s=s.replace(anchor,block+anchor,1)
 s=s.replace('  inventory-db-data:', '  payment-db-data:\n  inventory-db-data:')
 compose.write_text(s)
print('Payment module registered; payment-db added to Docker Compose.')
