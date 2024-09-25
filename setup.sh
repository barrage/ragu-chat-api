# Set up the git hooks
ln -f hooks/pre-push .git/hooks/

# Set up the docker containers
docker compose up -d

# Set up the gradle properties
cp gradle.example.properties gradle.properties

# Set up the application properties
cp ./config/application.example.yaml ./config/application.yaml
