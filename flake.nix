{
  description = "Apache Directory Studio - arm64 macOS app build";

  inputs = {
    nixpkgs.url = "github:NixOS/nixpkgs/nixos-unstable";
    flake-utils.url = "github:numtide/flake-utils";
  };

  outputs = { self, nixpkgs, flake-utils }:
    flake-utils.lib.eachDefaultSystem (system:
      let
        pkgs = import nixpkgs { inherit system; };
        # JDK 17, not 21: the build uses the old antlr-maven-plugin (ANTLR 2.x)
        # which calls System.setSecurityManager(). On JDK 18+ that throws
        # (security manager removal), and the workaround
        # -Djava.security.manager=allow breaks Tycho's embedded Equinox runtime.
        # JDK 17 still allows setSecurityManager, satisfying both.
        jdk = pkgs.jdk17;

        # Apache Directory Studio is built with Maven + Eclipse Tycho.
        # The build resolves dependencies from Eclipse p2 update sites and
        # Maven Central at build time, so it cannot run inside the pure Nix
        # sandbox. We use __noChroot to allow network access; this requires
        # `sandbox = relaxed` (or sandbox disabled, the default on darwin)
        # in your nix.conf, or building with `--option sandbox relaxed`.
        directory-studio = pkgs.stdenv.mkDerivation {
          pname = "apache-directory-studio";
          version = "2.0.0-SNAPSHOT";

          src = ./.;

          __noChroot = true;

          nativeBuildInputs = [ jdk pkgs.maven ];

          JAVA_HOME = jdk.home;
          MAVEN_OPTS = "-Xmx1024m";

          buildPhase = ''
            runHook preBuild

            export HOME=$TMPDIR
            local_repo="$TMPDIR/m2-repo"

            # Step 1: build the Eclipse target platform + generate MANIFESTs
            mvn -f pom-first.xml clean install \
              -Dmaven.repo.local="$local_repo" \
              --batch-mode

            # Step 2: build the Tycho artifacts and materialize the products
            mvn clean install \
              -Dmaven.repo.local="$local_repo" \
              -DskipTests \
              --batch-mode

            runHook postBuild
          '';

          installPhase = ''
            runHook preInstall

            app=$(find product/target/products \
              -type d -path '*macosx/cocoa/aarch64*' -name 'ApacheDirectoryStudio.app' \
              | head -n1)

            if [ -z "$app" ]; then
              echo "ERROR: arm64 macOS .app not found under product/target/products" >&2
              find product/target/products -maxdepth 6 -name '*.app' -o -name '*.tar.gz' >&2 || true
              exit 1
            fi

            mkdir -p "$out/Applications"
            cp -R "$app" "$out/Applications/"

            runHook postInstall
          '';

          meta = with pkgs.lib; {
            description = "Eclipse-based LDAP browser and directory client (arm64 macOS app)";
            homepage = "https://directory.apache.org/studio/";
            license = licenses.asl20;
            platforms = [ "aarch64-darwin" ];
          };
        };
      in
      {
        packages = pkgs.lib.optionalAttrs (system == "aarch64-darwin") {
          default = directory-studio;
          directory-studio = directory-studio;
        };

        devShells.default = pkgs.mkShell {
          packages = [ jdk pkgs.maven ];
          shellHook = ''
            export JAVA_HOME="${jdk.home}"
            export MAVEN_OPTS="-Xmx1024m"
            echo "Apache Directory Studio dev shell (JDK 17 + Maven)"
            echo "Build with: ./build.sh"
          '';
        };
      });
}
