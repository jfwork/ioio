package hr.djajcevic.spc.ioio.looper.process;

import hr.djajcevic.spc.calculator.SunPositionCalculator;
import hr.djajcevic.spc.calculator.SunPositionData;
import hr.djajcevic.spc.ioio.looper.AxisController;
import hr.djajcevic.spc.ioio.looper.compas.CompassData;
import hr.djajcevic.spc.ioio.looper.compas.CompassReader;
import hr.djajcevic.spc.ioio.looper.gps.GPSData;
import hr.djajcevic.spc.ioio.looper.gps.GPSReader;
import ioio.lib.api.IOIO;
import ioio.lib.api.exception.ConnectionLostException;
import ioio.lib.util.IOIOLooper;
import lombok.Getter;

import java.util.LinkedHashSet;
import java.util.Set;

/**
 * @author djajcevic | 11.08.2015.
 */
public class SystemManager implements IOIOLooper, GPSReader.Delegate, CompassReader.Delegate {

    @Getter
    Set<SystemManagerListener> listeners = new LinkedHashSet<SystemManagerListener>();
    private IOIO ioio;
    @Getter
    private AxisController xAxisController;
    @Getter
    private AxisController yAxisController;
    @Getter
    private CompassReader compassReader;
    @Getter
    private CompassData compassData;
    @Getter
    private GPSReader gpsReader;
    @Getter
    private GPSData gpsData;

    private CalibrationProcessManager calibrationManager;
    private ParkingProcessManager parkingManager;

    private boolean parkedX;
    private boolean parkedY;

    @Getter
    private SunPositionData sunPositionData;

    private void initialize() throws ConnectionLostException, InterruptedException {

        xAxisController = new AxisController(new AxisController.Delegate() {
            @Override
            public void stepCompleted(final int currentStep) {
                for (SystemManagerListener listener : listeners) {
                    listener.xAxisStepCompleted(currentStep);
                }
            }

            @Override
            public void reachedStartPosition() {
                for (SystemManagerListener listener : listeners) {
                    listener.xAxisReachedStartPosition();
                }
                parkedX = true;
            }

            @Override
            public void reachedEndPosition() {
                for (SystemManagerListener listener : listeners) {
                    listener.xAxisReachedEndPosition();
                }
            }

            @Override
            public boolean shouldStop(final boolean positiveDirection, final int currentStep) {
                return false;
            }
        }, ioio, AxisController.Axis.X);

        yAxisController = new AxisController(new AxisController.Delegate() {
            @Override
            public void stepCompleted(final int currentStep) {
                for (SystemManagerListener listener : listeners) {
                    listener.yAxisStepCompleted(currentStep);
                }
            }

            @Override
            public void reachedStartPosition() {
                for (SystemManagerListener listener : listeners) {
                    listener.yAxisReachedStartPosition();
                }
                parkedY = true;
            }

            @Override
            public void reachedEndPosition() {
                for (SystemManagerListener listener : listeners) {
                    listener.yAxisReachedEndPosition();
                }
            }

            @Override
            public boolean shouldStop(final boolean positiveDirection, final int currentStep) {
                return false;
            }
        }, ioio, AxisController.Axis.Y);

        xAxisController.initialize();
        yAxisController.initialize();

        gpsReader = new GPSReader(ioio, this);
        compassReader = new CompassReader(ioio, this);

        gpsReader.initialize();
        compassReader.initialize();

        calibrationManager = new CalibrationProcessManager();
        calibrationManager.setManagerRepository(this);
        calibrationManager.initialize();

        parkingManager = new ParkingProcessManager();
        parkingManager.setManagerRepository(this);
        parkingManager.initialize();

        sunPositionData = new SunPositionData();
    }


    @Override
    public void positionLocked(final GPSData data) {
        gpsData = data;
        for (SystemManagerListener listener : listeners) {
            listener.gpsPositionLocked(data);
        }
    }

    @Override
    public void dataReady(final CompassData data) {
        compassData = data;
        for (SystemManagerListener listener : listeners) {
            listener.compassDataReady(data);
        }
    }

    @Override
    public void setup(final IOIO ioio) throws ConnectionLostException, InterruptedException {
        this.ioio = ioio;
        initialize();
        for (SystemManagerListener listener : listeners) {
            listener.boardConnected(ioio);
        }
    }

    @Override
    public void loop() throws ConnectionLostException, InterruptedException {
        performRunActions();
    }

    private void performRunActions() {
        park();
        calibrate();
        calculateNextPosition();
        position();
    }

    private void park() {
        System.out.println("Parking system...");
        try {
            parkingManager.performManagementActions();
        } catch (ConnectionLostException e) {
            e.printStackTrace();
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
        System.out.println("Parking finished.");
    }

    private void calibrate() {
        System.out.println("Calibrating system...");
        try {
            calibrationManager.performManagementActions();
        } catch (ConnectionLostException e) {
            e.printStackTrace();
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
        System.out.println("System calibration finished.");
    }

    private void calculateNextPosition() {
        System.out.println("Calculating next position...");

        sunPositionData.latitude = gpsData.getLatitude();
        sunPositionData.longitude = gpsData.getLongitude();
        sunPositionData.elevation = gpsData.getAltitude();
        SunPositionCalculator.calculateSunPosition(sunPositionData);

        System.out.println("Azimuth: " + sunPositionData.azimuth + ", Zenith: " + sunPositionData.zenith);

        System.out.println("Calculation finished.");
    }

    private void position() {
        System.out.println("Positioning system...");

        System.out.println("Positioning finished.");
    }

    @Override
    public void disconnected() {
        for (SystemManagerListener listener : listeners) {
            listener.boardDisconnected();
        }
    }

    @Override
    public void incompatible() {
        incompatible(ioio);
    }

    @Override
    public void incompatible(final IOIO ioio) {
        for (SystemManagerListener listener : listeners) {
            listener.incompatibleBoard(ioio);
        }
    }
}
