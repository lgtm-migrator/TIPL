DELIMETER //;
DROP FUNCTION IF EXISTS `LINSPACE`//

CREATE FUNCTION LINSPACE(xin FLOAT, xstart FLOAT, xend FLOAT, xbins INT) 
RETURNS INT 
DETERMINISTIC
BEGIN
    DECLARE xout INT;
    SET xout=0;
    RETURN xout;
 END //
DROP FUNCTION IF EXISTS `LINSPACE`
