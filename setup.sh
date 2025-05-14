# Set up the docker containers
docker compose up -d

# Set up the gradle properties if not already present
if [ -e gradle.properties ]; then
  echo "gradle.properties already exists, skipping"
else
  cp gradle.example.properties gradle.properties
fi

# Set up the application properties
if [ -e config/application.conf ]; then
  echo "application.conf already exists, skipping"
else
  cp ./config/application.example.conf ./config/application.conf
fi

./gradlew generateJooq

psql postgresql://postgres:postgres@localhost:5454/kappi -f src/main/resources/db/seed/initial.sql

exit 0
