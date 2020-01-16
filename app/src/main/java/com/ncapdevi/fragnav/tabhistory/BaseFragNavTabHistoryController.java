package com.ncapdevi.fragnav.tabhistory;

import com.ncapdevi.fragnav.FragNavPopController;

abstract class BaseFragNavTabHistoryController implements FragNavTabHistoryController {
    final FragNavPopController fragNavPopController;

    BaseFragNavTabHistoryController(FragNavPopController fragNavPopController) {
        this.fragNavPopController = fragNavPopController;
    }
}
