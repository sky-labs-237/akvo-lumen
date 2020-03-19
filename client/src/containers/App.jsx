import React from 'react';
import PropTypes from 'prop-types';
import { Router, Route, Redirect, useLocation } from 'react-router-dom';
import _ from 'lodash';
import IntlWrapper from './IntlWrapper';
import Library from '../components/Library';
import Visualisation from './Visualisation';
import Dataset from './Dataset';
import Raster from './Raster';
import Transformation from './Transformation';
import Dashboard from '../components/dashboard/Dashboard';
import Users from '../components/users/Users';
import Resources from '../components/Resources';
import Main from './Main';
import WorkspaceNav from '../components/WorkspaceNav';
import AdminNav from '../components/AdminNav';
import withProps from '../utilities/withProps';

function RouteToMain({ toRoute, toMain }) {
  const location = useLocation();
  toMain.location = location;
  console.log({ toRoute, toMain });
  return (
    <Route
      {...toRoute}
      render={routeProps => (
          <Main  {...Object.assign({}, routeProps, toMain)} />
      )}
    />
  );
}

export default function App({ store, history, query }) {
  const path = ['profile', 'https://akvo.org/app_metadata', 'lumen', 'features', 'filteredDashboard'];
  const filteredDashboard = !((store && _.get(store.getState(), path)) === false);
  const queryParsed = (query && JSON.parse(query)) || {};

  return (
    <IntlWrapper>
      <Router history={history}>
        <RouteToMain
          toRoute={{path: "/admin/users"}}
          toMain={{sidebar: <AdminNav/>, content: <Users/>}}
          />        
      </Router>
    </IntlWrapper>
  );
}

App.propTypes = {
  history: PropTypes.object.isRequired,
  location: PropTypes.object,
  store: PropTypes.object,
  query: PropTypes.string,
};
