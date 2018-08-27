/* eslint-disable no-underscore-dangle */
import fetch from 'isomorphic-fetch';
import Keycloak from 'keycloak-js';
import Raven from 'raven-js';
import queryString from 'query-string';

let keycloak = null;

export function token() {
  if (!keycloak) {
    return Promise.resolve(window.accessToken);
  }

  return new Promise(resolve =>
    keycloak
      .updateToken()
      .success(() => resolve(keycloak.token))
      .error(() => {
        // Redirect to login page
        keycloak.login();
      })
  );
}

export function refreshToken() {
  if (keycloak == null) {
    throw new Error('Keycloak not initialized');
  }
  return keycloak.refreshToken;
}

export function init() {
  if (keycloak != null) {
    throw new Error('Keycloak already initialized');
  }
  return fetch('/env')
    .then(response => response.json())
    .then(
      ({ keycloakClient, keycloakURL, tenant, sentryDSN, flowApiUrl, piwikSiteId, exporterUrl }) =>
        new Promise((resolve, reject) => {
          if (process.env.NODE_ENV === 'production') {
            Raven.config(sentryDSN).install();
            Raven.setExtraContext({ tenant });
          }
          keycloak = new Keycloak({
            url: keycloakURL,
            realm: 'akvo',
            clientId: keycloakClient,
          });

          const queryParams = queryString.parse(location.search);

          keycloak
            .init({
              onLoad: 'login-required',
              checkLoginIframe: false,
              token: queryParams.token,
              refreshToken: queryParams.refresh_token,
            })
            .success((authenticated) => {
              if (authenticated) {
                keycloak
                  .loadUserProfile()
                  .success((profile) => {
                    if (process.env.NODE_ENV === 'production') {
                      Raven.setUserContext(profile);
                    }
                    resolve({
                      profile: Object.assign({}, profile, {
                        admin: keycloak.hasRealmRole(`akvo:lumen:${tenant}:admin`),
                      }),
                      env: { flowApiUrl, keycloakURL, piwikSiteId, exporterUrl },
                    });
                  })
                  .error(() => {
                    reject(new Error('Could not load user profile'));
                  });
              } else {
                reject(new Error('Could not authenticate'));
              }
            })
            .error(() => {
              reject(new Error('Login attempt failed'));
            });
        })
    );
}

export function initPublic() {
  return fetch('/env')
    .then(response => response.json())
    .then(env => ({ env }));
}
