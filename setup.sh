# Set up the git hooks
ln -f hooks/pre-push .git/hooks/

# Set up the docker containers
docker compose up -d

# Set up the gradle properties if not already present
cat gradle.properties &> /dev/null
if [ $? -eq 0 ]; then
  echo "gradle.properties already exists, skipping"
else
  cp gradle.example.properties gradle.properties
fi

# Set up the application properties
cat ./config/application.yaml &> /dev/null

if [ $? -eq 0 ]; then
  echo "application.yaml already exists, skipping"
else
  cp ./config/application.example.yaml ./config/application.yaml
fi

# Set up the liquibase properties
cat liquibase.properties &> /dev/null

if [ $? -eq 0 ]; then
  echo "liquibase.properties already exists, skipping"
  exit 0;
else
  cp liquibase.example.properties liquibase.properties
fi
