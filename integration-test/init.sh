docker-compose -f docker-compose-init.yaml up -d

docker-compose -f docker-compose-init.yaml exec kafka-ocp \
  kafka-topics.sh --create --topic rep1 --partitions 2 --replication-factor 1 --bootstrap-server kafka-ocp:9092

docker-compose -f docker-compose-init.yaml exec kafka-ocp \
  kafka-topics.sh --create --topic rep2 --partitions 2 --replication-factor 1 --bootstrap-server kafka-ocp:9092

docker-compose -f docker-compose-init.yaml exec kafka-ocp \
  kafka-topics.sh --create --topic norep1 --partitions 2 --replication-factor 1 --bootstrap-server kafka-ocp:9092

docker-compose -f docker-compose-init.yaml exec kafka-cld \
  kafka-topics.sh --create --topic rep1 --partitions 2 --replication-factor 1 --bootstrap-server kafka-cld:9094
  
docker-compose -f docker-compose-init.yaml exec kafka-cld \
  kafka-topics.sh --create --topic rep2 --partitions 2 --replication-factor 1 --bootstrap-server kafka-cld:9094

docker-compose -f docker-compose-init.yaml exec kafka-cld \
  kafka-topics.sh --create --topic norep2 --partitions 2 --replication-factor 1 --bootstrap-server kafka-cld:9094