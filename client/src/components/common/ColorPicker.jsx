import React from 'react';
import PropTypes from 'prop-types';
import SwatchesPicker from 'react-color/lib/components/swatches/Swatches';
import Popover from './Popover';

import './ColorPicker.scss';
import LegendShape from '../charts/LegendShape';

const ColorPicker = ({ title, left = 0, top = 0, placement, style, hideArrow, ...rest }) => (
  <Popover
    left={left}
    top={top}
    placement={placement}
    hideArrow={hideArrow}
    className="color-picker-popover"
    style={style}
    title={(
      <span>
        {rest.color && <LegendShape fill={rest.color} isActive />}
        {title}
      </span>
    )}
  >
    <div className="color-picker">
      <SwatchesPicker {...rest} />
    </div>
  </Popover>
);

ColorPicker.propTypes = {
  target: PropTypes.node,
  isOpen: PropTypes.bool,
  hideArrow: PropTypes.bool,
  title: PropTypes.string,
  placement: PropTypes.string,
  left: PropTypes.number,
  top: PropTypes.number,
  style: PropTypes.object,
};

export default ColorPicker;
